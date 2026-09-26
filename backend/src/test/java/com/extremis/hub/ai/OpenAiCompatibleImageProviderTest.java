package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real HTTP round trips against an in-process fake server -- exercises the
 * actual request building (auth header, JSON body, size format, optional
 * reference image) and response parsing, including the failure shapes
 * observed live (HTTP 413 on an oversized base64 reference image, no
 * "model" field in a real response).
 */
class OpenAiCompatibleImageProviderTest {

    private HttpServer server;
    private final AtomicReference<String> seenPath = new AtomicReference<>();
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseJson = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenPath.set(exchange.getRequestURI().getPath());
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleImageProvider provider(String path) {
        OpenAiCompatibleImageProperties p = new OpenAiCompatibleImageProperties();
        p.setGenerationsUrl("http://127.0.0.1:" + server.getAddress().getPort() + path);
        p.setApiKey("sk-test-key");
        p.setModel("qwen-image/z-image-turbo");
        p.setTimeoutSeconds(5);
        return new OpenAiCompatibleImageProvider(p);
    }

    @Test
    void sendsAuthModelPromptAndSizeInTheStarSeparatedFormat() {
        responseJson = """
            {"created":1,"data":[{"url":"https://oss.example/img.png"}],
             "usage":{"width":1280,"height":720,"image_count":1,"input_tokens":0,"output_tokens":0,"total_tokens":0}}""";

        ImageGenerationResult r = provider("/v1/images/generations")
            .generate(new ImageGenerationRequest("a gaming thumbnail", 1280, 720));

        assertThat(seenPath.get()).isEqualTo("/v1/images/generations");
        assertThat(seenAuth.get()).isEqualTo("Bearer sk-test-key");
        assertThat(seenBody.get())
            .contains("\"model\":\"qwen-image/z-image-turbo\"")
            .contains("\"prompt\":\"a gaming thumbnail\"")
            .contains("\"size\":\"1280*720\"") // star-separated, not "x"
            .doesNotContain("\"image\""); // no reference image on the 3-arg constructor
        assertThat(r.imageUrl()).isEqualTo("https://oss.example/img.png");
        assertThat(r.width()).isEqualTo(1280);
        assertThat(r.height()).isEqualTo(720);
        // no "model" field in the real response -- servedBy falls back to the configured alias
        assertThat(r.servedBy()).isEqualTo("qwen-image/z-image-turbo");
    }

    @Test
    void sendsTheReferenceImageFieldOnlyWhenOnePassed() {
        responseJson = "{\"data\":[{\"url\":\"https://oss.example/img.png\"}]}";

        provider("/v1").generate(new ImageGenerationRequest(
            "make it blue", "data:image/png;base64,AAAA", 512, 512, 1));

        assertThat(seenBody.get()).contains("\"image\":\"data:image/png;base64,AAAA\"");
    }

    @Test
    void fallsBackToTheRequestedSizeWhenUsageIsMissing() {
        responseJson = "{\"data\":[{\"url\":\"https://oss.example/img.png\"}]}";

        ImageGenerationResult r = provider("/v1").generate(new ImageGenerationRequest("x", 512, 512));

        assertThat(r.width()).isEqualTo(512);
        assertThat(r.height()).isEqualTo(512);
    }

    @Test
    void requestEntityTooLargeIsReportedWithoutLeakingTheBody() {
        status = 413;
        responseJson = "nginx default 413 body, not JSON";

        assertThatThrownBy(() -> provider("/v1").generate(new ImageGenerationRequest("x", 512, 512)))
            .isInstanceOf(ImageGenerationException.class)
            .hasMessage("Image gateway returned HTTP 413");
    }

    @Test
    void emptyDataIsAFailure() {
        responseJson = "{\"data\":[]}";

        assertThatThrownBy(() -> provider("/v1").generate(new ImageGenerationRequest("x", 512, 512)))
            .isInstanceOf(ImageGenerationException.class);
    }

    @Test
    void connectionFailureIsWrappedNotLeaked() {
        OpenAiCompatibleImageProperties p = new OpenAiCompatibleImageProperties();
        p.setGenerationsUrl("http://127.0.0.1:1/v1/images/generations"); // nothing listens here
        p.setApiKey("k");
        p.setModel("m");
        p.setTimeoutSeconds(2);

        assertThatThrownBy(() -> new OpenAiCompatibleImageProvider(p).generate(new ImageGenerationRequest("x", 512, 512)))
            .isInstanceOf(ImageGenerationException.class);
    }
}

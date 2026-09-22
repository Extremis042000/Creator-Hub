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
 * Real HTTP round trips against an in-process fake server -- exercises
 * the actual request building (auth header, JSON body, token floor) and
 * response parsing, including the failure shapes observed on the live
 * free-tier gateway (empty content on finish_reason=length, HTTP 402).
 */
class OpenAiCompatibleGenerationProviderTest {

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

    private OpenAiCompatibleGenerationProvider provider(String path) {
        OpenAiCompatibleProperties p = new OpenAiCompatibleProperties();
        p.setChatUrl("http://127.0.0.1:" + server.getAddress().getPort() + path);
        p.setApiKey("sk-test-key");
        p.setModel("fm-v1-lite");
        p.setTimeoutSeconds(5);
        return new OpenAiCompatibleGenerationProvider(p);
    }

    private static AiGenerationRequest req() {
        return new AiGenerationRequest("sys prompt", "user prompt", 900);
    }

    @Test
    void sendsAuthModelMessagesAndRaisesTheTokenBudgetToTheFloor() {
        responseJson = """
            {"model":"upstream/some-model:free","choices":[{"finish_reason":"stop",
             "message":{"role":"assistant","content":"hello","reasoning":"ignored"}}],
             "usage":{"prompt_tokens":11,"completion_tokens":7,"cost":0}}""";

        AiGenerationResult r = provider("/v1/chat/completions").generate(req());

        assertThat(seenPath.get()).isEqualTo("/v1/chat/completions"); // posts to exactly the configured URL
        assertThat(seenAuth.get()).isEqualTo("Bearer sk-test-key");
        assertThat(seenBody.get()).contains("\"model\":\"fm-v1-lite\"")
            .contains("\"max_tokens\":3000") // 900 requested, floored to 3000
            .contains("sys prompt").contains("user prompt");
        assertThat(r.text()).isEqualTo("hello");
        assertThat(r.inputTokens()).isEqualTo(11);
        assertThat(r.outputTokens()).isEqualTo(7);
        assertThat(r.servedBy()).isEqualTo("upstream/some-model:free");
    }

    @Test
    void phase29HistoryTurnsAreSentInOrderBetweenSystemAndTheFinalUserMessage() {
        responseJson = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

        provider("/v1").generate(new AiGenerationRequest("sys prompt",
            java.util.List.of(
                new ConversationTurn(ConversationTurn.Role.ASSISTANT, "first assistant turn"),
                new ConversationTurn(ConversationTurn.Role.USER, "first refine instruction")),
            "second refine instruction", 900));

        // Map.of's own field iteration order is unspecified, so this only asserts relative
        // message ORDER (system, then each history turn in order, then the final user
        // prompt) -- not the field order within any one message object.
        String body = seenBody.get();
        int sysIdx = body.indexOf("sys prompt");
        int assistantIdx = body.indexOf("first assistant turn");
        int userIdx = body.indexOf("first refine instruction");
        int finalIdx = body.indexOf("second refine instruction");
        assertThat(sysIdx).isGreaterThanOrEqualTo(0);
        assertThat(sysIdx).isLessThan(assistantIdx);
        assertThat(assistantIdx).isLessThan(userIdx);
        assertThat(userIdx).isLessThan(finalIdx);
    }

    @Test
    void aLargerRequestedBudgetIsNotShrunk() {
        responseJson = "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}";

        provider("/v1").generate(new AiGenerationRequest("s", "u", 8000));

        assertThat(seenBody.get()).contains("\"max_tokens\":8000");
    }

    @Test
    void stripsLeakedThinkBlocks() {
        responseJson = "{\"choices\":[{\"message\":{\"content\":\"<think>plan\\nmore</think>  {\\\"ok\\\":true}\"}}]}";

        assertThat(provider("/v1").generate(req()).text()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void nullContentFromExhaustedReasoningBudgetIsAFailureNotAnEmptySuccess() {
        responseJson = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}]}";

        assertThatThrownBy(() -> provider("/v1").generate(req()))
            .isInstanceOf(AiGenerationException.class)
            .hasMessageContaining("finish_reason=length");
    }

    @Test
    void paymentRequiredIsReportedWithoutLeakingTheBody() {
        status = 402;
        responseJson = "{\"error\":\"secret upstream detail sk-leak\"}";

        assertThatThrownBy(() -> provider("/v1").generate(req()))
            .isInstanceOf(AiGenerationException.class)
            .hasMessage("AI gateway returned HTTP 402");
    }

    @Test
    void emptyChoicesIsAFailure() {
        responseJson = "{\"choices\":[]}";

        assertThatThrownBy(() -> provider("/v1").generate(req()))
            .isInstanceOf(AiGenerationException.class);
    }

    @Test
    void connectionFailureIsWrappedNotLeaked() {
        OpenAiCompatibleProperties p = new OpenAiCompatibleProperties();
        p.setChatUrl("http://127.0.0.1:1/v1/chat/completions"); // nothing listens here
        p.setApiKey("k");
        p.setModel("m");
        p.setTimeoutSeconds(2);

        assertThatThrownBy(() -> new OpenAiCompatibleGenerationProvider(p).generate(req()))
            .isInstanceOf(AiGenerationException.class);
    }
}

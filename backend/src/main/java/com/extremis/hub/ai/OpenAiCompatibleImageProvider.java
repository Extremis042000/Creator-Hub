package com.extremis.hub.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Single-shot image generation over the FreeModel gateway's
 * /v1/images/generations -- a different request/response shape from the
 * chat-completions text path (see OpenAiCompatibleImageProperties).
 * Live-verified (2026-09-26) against the real production key, resolving
 * docs/decisions/10-ai-thumbnail-generator.md §3's three open questions:
 *
 *  - 1280x720 (16:9, real YouTube thumbnail resolution) generates
 *    correctly and visually well (size sent as "1280*720", not
 *    "1280x720"); 512x512 also verified.
 *  - a base64 data URI reference image (request field "image") is
 *    genuinely used, not just accepted-and-ignored -- confirmed by asking
 *    for a colour change and getting back the same subject/pose
 *    recoloured. This means no new image-upload/storage infra is needed
 *    for the founder's "user can upload a sample photo" ask.
 *  - the gateway itself rejects an oversized request body with a raw HTTP
 *    413 (nginx-level, not a JSON API error) somewhere between a ~120KB
 *    and ~1.37MB raw reference image (before base64 inflation) -- Phase
 *    41's upload path must downscale/compress a user-supplied reference
 *    image below REFERENCE_IMAGE_SAFE_MAX_BYTES before calling generate().
 *  - 23 rapid successive no-reference-image calls in one run (plus 4
 *    earlier the same session, 27 total) all succeeded with no 402/429
 *    and steady ~3.7-4.7s latency -- no free-tier ceiling found at this
 *    volume, unlike the text gateway's Phase 26b/26c findings which never
 *    hit one either at a comparable scale.
 *  - usage.input_tokens/output_tokens/total_tokens are always reported as
 *    0 for image calls -- no real per-call cost visibility, unlike text.
 *  - the response has no "model" field (unlike the text path's
 *    response.model()) -- ImageGenerationResult.servedBy() is therefore
 *    the CONFIGURED alias, not a live-detected upstream.
 */
public class OpenAiCompatibleImageProvider implements ImageGenerationProvider {

    /**
     * Live-verified (2026-09-26): a base64 reference image built from a
     * ~1.37MB raw PNG got HTTP 413; one from ~120KB succeeded. The real
     * ceiling sits somewhere between the two and wasn't pinned exactly --
     * this is a deliberately conservative safe line, not a measured
     * boundary.
     */
    public static final int REFERENCE_IMAGE_SAFE_MAX_BYTES = 120_000;

    private final OpenAiCompatibleImageProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleImageProvider(OpenAiCompatibleImageProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String getProviderName() {
        return "openai-compatible-image";
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("prompt", request.prompt());
        body.put("n", Math.max(1, request.maxImages()));
        body.put("size", request.width() + "*" + request.height());
        if (request.referenceImageDataUri() != null && !request.referenceImageDataUri().isBlank()) {
            body.put("image", request.referenceImageDataUri());
        }

        OpenAiImageResponse response;
        try {
            response = restClient.post()
                .uri(properties.getGenerationsUrl())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OpenAiImageResponse.class);
        } catch (RestClientResponseException e) {
            throw new ImageGenerationException("Image gateway returned HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new ImageGenerationException("Image gateway request failed: " + e.getClass().getSimpleName(), e);
        }

        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().get(0).url() == null || response.data().get(0).url().isBlank()) {
            throw new ImageGenerationException("Image gateway returned no image.");
        }

        int servedWidth = response.usage() != null && response.usage().width() != null
            ? response.usage().width() : request.width();
        int servedHeight = response.usage() != null && response.usage().height() != null
            ? response.usage().height() : request.height();

        return new ImageGenerationResult(response.data().get(0).url(), servedWidth, servedHeight, properties.getModel());
    }

    /** The subset of the gateway's image-generation response we read; everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiImageResponse(List<ImageData> data, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ImageData(String url) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Usage(Integer width, Integer height) {
        }
    }
}

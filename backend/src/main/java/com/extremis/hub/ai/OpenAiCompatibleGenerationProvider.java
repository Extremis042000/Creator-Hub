package com.extremis.hub.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Single-shot text generation over any OpenAI-style /chat/completions
 * endpoint. Verified live (2026-09-22) against the FreeModel gateway's
 * free tier: valid JSON output 5/5 at max_tokens=3000, ~4s per call --
 * and the gateway's "fm-v1-lite" is a small free upstream model
 * (reported in the response's "model" field), not Claude, so output
 * quality is a template-comparable baseline, not a frontier-model one.
 *
 * Failure modes handled explicitly because free gateways exhibit them:
 * HTTP errors (402 = the paid tiers), null/empty content when hidden
 * reasoning eats the budget, and <think> blocks leaking into content.
 * Errors carry no response body or prompt text.
 */
public class OpenAiCompatibleGenerationProvider implements AiGenerationProvider {

    private final OpenAiCompatibleProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleGenerationProvider(OpenAiCompatibleProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        int maxTokens = Math.max(request.maxOutputTokens(), properties.getMaxTokensFloor());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
            Map.of("role", "system", "content", request.systemPrompt()),
            Map.of("role", "user", "content", request.userPrompt())));

        OpenAiChatResponse response;
        try {
            response = restClient.post()
                .uri(chatCompletionsUrl())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OpenAiChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new AiGenerationException("AI gateway returned HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new AiGenerationException("AI gateway request failed: " + e.getClass().getSimpleName(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new AiGenerationException("AI gateway returned no choices.");
        }

        OpenAiChatResponse.Choice choice = response.choices().get(0);
        String text = choice.message().content() == null
            ? "" : choice.message().content().replaceAll("(?s)<think>.*?</think>", "").trim();
        if (text.isEmpty()) {
            throw new AiGenerationException(
                "AI gateway returned no text (finish_reason=" + choice.finishReason() + ").");
        }

        long in = response.usage() != null && response.usage().promptTokens() != null
            ? response.usage().promptTokens() : 0;
        long out = response.usage() != null && response.usage().completionTokens() != null
            ? response.usage().completionTokens() : 0;
        // "model" is the upstream that actually served the call -- gateways
        // route, so it can differ from the configured alias and over time.
        return new AiGenerationResult(text, in, out, response.model());
    }

    private String chatCompletionsUrl() {
        String base = properties.getBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/chat/completions";
    }
}

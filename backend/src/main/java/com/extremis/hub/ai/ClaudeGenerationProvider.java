package com.extremis.hub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tier-1 ("single-agent triage") pattern from the founder's Triage Desk
 * project, via the official Anthropic Java SDK instead of spawning the
 * claude CLI -- see docs/decisions/07-ai-generation-initiative.md §3 for
 * why. One prompt in, one text out; no tools, no session; maxTokens is
 * the per-call cost ceiling.
 *
 * NOT yet exercised against the live API: no ANTHROPIC_API_KEY exists
 * yet (founder action). Verify with POST /api/v1/admin/ai/ping once set.
 * Thinking is left at the model default (adaptive on current models)
 * rather than disabled -- disabling it has known failure modes on
 * Claude Opus 5; cost is controlled via effort instead.
 */
public class ClaudeGenerationProvider implements AiGenerationProvider {

    private final AnthropicClient client;
    private final AiProperties properties;

    public ClaudeGenerationProvider(AnthropicClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens((long) request.maxOutputTokens())
                .system(request.systemPrompt())
                .outputConfig(OutputConfig.builder().effort(effort()).build())
                .addUserMessage(request.userPrompt())
                .build();

            Message response = client.messages().create(params);

            if (response.stopDetails().isPresent()) {
                throw new AiGenerationException("Claude declined this request.");
            }

            String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining())
                .trim();
            if (text.isEmpty()) {
                throw new AiGenerationException("Claude returned no text.");
            }

            return new AiGenerationResult(
                text, response.usage().inputTokens(), response.usage().outputTokens());
        } catch (AiGenerationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiGenerationException("Claude request failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private OutputConfig.Effort effort() {
        return switch (properties.getEffort() == null ? "" : properties.getEffort().trim().toLowerCase(Locale.ROOT)) {
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.LOW;
        };
    }
}

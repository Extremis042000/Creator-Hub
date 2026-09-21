package com.extremis.hub.ai;

import com.extremis.hub.admin.AdminService;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only smoke test for the AI provider -- the way to prove a newly
 * supplied ANTHROPIC_API_KEY actually works end to end (one tiny call,
 * a few cents at most) without touching any customer-facing tool.
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminService adminService;
    private final Optional<AiGenerationProvider> aiProvider;

    @PostMapping("/ping")
    public Map<String, Object> ping(Authentication authentication) {
        adminService.requireAdmin(authentication);
        AiGenerationProvider provider = aiProvider.orElseThrow(() -> new BusinessRuleViolationException(
            "AI generation isn't configured -- set ANTHROPIC_API_KEY (and leave AI_GENERATION_ENABLED on)."));

        long started = System.currentTimeMillis();
        AiGenerationResult result = provider.generate(new AiGenerationRequest(
            "You are a connectivity check. Reply with exactly one word.", "Reply with the word: pong", 512));

        return Map.of(
            "provider", provider.getProviderName(),
            "model", provider.getModelName(),
            "reply", result.text(),
            "inputTokens", result.inputTokens(),
            "outputTokens", result.outputTokens(),
            "latencyMs", System.currentTimeMillis() - started);
    }
}

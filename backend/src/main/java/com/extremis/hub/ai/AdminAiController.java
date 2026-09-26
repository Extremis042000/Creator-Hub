package com.extremis.hub.ai;

import com.extremis.hub.admin.AdminService;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only AI endpoints: a smoke test for the provider (prove a newly
 * supplied key works end to end, a few cents at most, without touching
 * any customer-facing tool) and Phase 28's usage-today summary (real
 * cost/abuse visibility -- see AiUsageGuard/AiUsageService).
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminService adminService;
    private final Optional<AiGenerationProvider> aiProvider;
    private final Optional<ImageGenerationProvider> imageProvider;
    private final AiUsageService aiUsageService;

    @GetMapping("/usage-today")
    public AiUsageSummaryResponse usageToday(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return aiUsageService.getTodaySummary();
    }

    /** Phase 38: all-time copy/refine/regenerate signal breakdown per tool -- see AiUsageService.getSignalSummary. */
    @GetMapping("/signals")
    public AiSignalSummaryResponse signals(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return aiUsageService.getSignalSummary();
    }

    @PostMapping("/ping")
    public Map<String, Object> ping(Authentication authentication) {
        adminService.requireAdmin(authentication);
        AiGenerationProvider provider = aiProvider.orElseThrow(() -> new BusinessRuleViolationException(
            "AI generation isn't configured -- set AI_API_BASE_URL/AI_API_KEY/AI_MODEL (or ANTHROPIC_API_KEY with AI_PROVIDER=anthropic)."));

        long started = System.currentTimeMillis();
        AiGenerationResult result = provider.generate(new AiGenerationRequest(
            "You are a connectivity check. Reply with exactly one word.", "Reply with the word: pong", 512));

        return Map.of(
            "provider", provider.getProviderName(),
            "model", provider.getModelName(),
            "servedBy", result.servedBy() == null ? "unknown" : result.servedBy(),
            "reply", result.text(),
            "inputTokens", result.inputTokens(),
            "outputTokens", result.outputTokens(),
            "latencyMs", System.currentTimeMillis() - started);
    }

    /** Phase 40: same idea as /ping, for the image path -- a real, low-cost end-to-end check. */
    @PostMapping("/ping-image")
    public Map<String, Object> pingImage(Authentication authentication) {
        adminService.requireAdmin(authentication);
        ImageGenerationProvider provider = imageProvider.orElseThrow(() -> new BusinessRuleViolationException(
            "Image generation isn't configured -- set AI_IMAGE_API_URL/AI_IMAGE_MODEL (reuses AI_API_KEY unless AI_IMAGE_API_KEY is set)."));

        long started = System.currentTimeMillis();
        ImageGenerationResult result = provider.generate(
            new ImageGenerationRequest("a plain grey square, connectivity check", 512, 512));

        return Map.of(
            "provider", provider.getProviderName(),
            "model", provider.getModelName(),
            "servedBy", result.servedBy() == null ? "unknown" : result.servedBy(),
            "imageUrl", result.imageUrl(),
            "width", result.width(),
            "height", result.height(),
            "latencyMs", System.currentTimeMillis() - started);
    }
}

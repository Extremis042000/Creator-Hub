package com.extremis.hub.tools.description;

import com.extremis.hub.admin.ForbiddenException;
import com.extremis.hub.admin.UnauthenticatedException;
import com.extremis.hub.ai.RefineRequest;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.premium.PremiumAccessService;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/gaming-description-generator")
@RequiredArgsConstructor
public class DescriptionGeneratorController {

    private final DescriptionGeneratorService descriptionGeneratorService;
    private final SharedResultService sharedResultService;
    private final PremiumAccessService premiumAccessService;

    @PostMapping
    public DescriptionGeneratorResponse generate(@Valid @RequestBody DescriptionGeneratorRequest request, Authentication authentication) {
        premiumAccessService.requireAccessIfPremium(ToolType.DESCRIPTION_GENERATOR, authentication);
        // AI-enhanced generation (Phase 27) is a separate, finer-grained gate than the
        // tool-level premiumOnly check above: any premium-entitled signed-in user gets
        // it, even on a tool that isn't itself marked premiumOnly. Free users keep the
        // deterministic templates unchanged.
        UUID userId = authentication != null ? (UUID) authentication.getPrincipal() : null;
        boolean useAi = userId != null && premiumAccessService.hasPremiumAccess(userId);
        DescriptionGeneratorResponse response = descriptionGeneratorService.generate(request, useAi, userId);

        if (request.isSave()) {
            String shareToken = sharedResultService.save(ToolType.DESCRIPTION_GENERATOR, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }

    /**
     * Phase 29: "make it shorter" on an AI-generated result. No
     * template fallback exists here, so this requires the same
     * premium entitlement generate() uses for its AI path -- a free or
     * signed-out caller never had an AI result (and thus no
     * refineSessionId) to refine in the first place.
     */
    @PostMapping("/refine")
    public DescriptionGeneratorResponse refine(@Valid @RequestBody RefineRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new UnauthenticatedException("Sign in required to refine a result.");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        if (!premiumAccessService.hasPremiumAccess(userId)) {
            throw new ForbiddenException("Refining a result requires premium access.");
        }
        return descriptionGeneratorService.refine(request.getSessionId(), request.getMessage(), userId);
    }
}

package com.extremis.hub.tools.thumbnail;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.premium.PremiumAccessService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 41. Unlike the other 5 tool controllers, there is no free
 * path at all here -- requireAccessIfPremium always throws for a
 * non-premium/signed-out caller, since this tool's seeded Tool row
 * (V12__seed_thumbnail_tool) has premiumOnly=true from the start.
 */
@RestController
@RequestMapping("/api/v1/tools/gaming-thumbnail-generator")
@RequiredArgsConstructor
public class ThumbnailGeneratorController {

    private final ThumbnailGeneratorService thumbnailGeneratorService;
    private final PremiumAccessService premiumAccessService;

    @PostMapping
    public ThumbnailGeneratorResponse generate(@Valid @RequestBody ThumbnailGeneratorRequest request, Authentication authentication) {
        // Throws UnauthenticatedException/ForbiddenException before this ever
        // reaches an unauthenticated caller -- authentication is guaranteed
        // non-null past this line since the tool is always premiumOnly.
        premiumAccessService.requireAccessIfPremium(ToolType.THUMBNAIL_GENERATOR, authentication);
        UUID userId = (UUID) authentication.getPrincipal();
        return thumbnailGeneratorService.generate(request, userId);
    }
}

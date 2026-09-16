package com.extremis.hub.premium;

import com.extremis.hub.admin.ForbiddenException;
import com.extremis.hub.admin.UnauthenticatedException;
import com.extremis.hub.domain.Tool;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.repository.ToolRepository;
import com.extremis.hub.repository.UserEntitlementRepository;
import com.extremis.hub.web.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "does this tool require premium, and
 * does this user have it" -- the real, server-side enforcement that
 * was missing before Phase 22 (marking a tool premiumOnly was purely
 * cosmetic: the frontend showed a lock icon, but the actual
 * /api/v1/tools/** endpoints never checked anything, so anyone could
 * call the API directly and bypass it entirely).
 *
 * No real subscription billing exists yet (PhonePe onboarding still
 * pending, and even once live, Phase 20 only built one-time digital
 * product purchases, not recurring subscriptions) -- so today, the
 * only way a user gets this entitlement is an admin grant
 * (AdminService#grantPremium). Revisit once real subscription billing
 * exists.
 */
@Service
@RequiredArgsConstructor
public class PremiumAccessService {

    /** One global entitlement covering every premiumOnly tool -- matches Tool's single boolean flag, not per-tool purchases. */
    public static final String PREMIUM_ENTITLEMENT_KEY = "premium-tools";

    private final ToolRepository toolRepository;
    private final UserEntitlementRepository userEntitlementRepository;

    /** No-op if the tool isn't premiumOnly. Throws if it is and the caller doesn't qualify. */
    public void requireAccessIfPremium(ToolType toolType, Authentication authentication) {
        Tool tool = toolRepository.findByToolType(toolType)
            .orElseThrow(() -> new ResourceNotFoundException("Tool not found."));
        if (!tool.isPremiumOnly()) {
            return;
        }
        if (authentication == null) {
            throw new UnauthenticatedException("Sign in required for this premium tool.");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        if (!hasPremiumAccess(userId)) {
            throw new ForbiddenException("This tool requires premium access.");
        }
    }

    public boolean hasPremiumAccess(UUID userId) {
        return userEntitlementRepository.findByUserIdAndEntitlementKey(userId, PREMIUM_ENTITLEMENT_KEY)
            .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(Instant.now()))
            .isPresent();
    }
}

package com.extremis.hub.payment;

import com.extremis.hub.admin.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/sessions")
    public CheckoutSessionResponse createSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new UnauthenticatedException("Sign in required.");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        return checkoutService.createSession(userId, request.getProductId());
    }
}

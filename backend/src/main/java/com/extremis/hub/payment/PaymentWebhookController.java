package com.extremis.hub.payment;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All headers are forwarded as-is -- different providers need
 * different header names for their signature scheme (Cashfree needs
 * x-webhook-signature + x-webhook-timestamp), so this controller
 * doesn't hardcode any provider's specific header names; the active
 * PaymentProvider implementation picks out what it needs.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final CheckoutService checkoutService;

    @PostMapping("/payment")
    public ResponseEntity<Void> handlePaymentWebhook(
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {
        checkoutService.handleWebhook(rawPayload, headers);
        return ResponseEntity.ok().build();
    }
}

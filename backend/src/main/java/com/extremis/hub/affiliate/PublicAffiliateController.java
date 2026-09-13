package com.extremis.hub.affiliate;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/affiliate")
@RequiredArgsConstructor
public class PublicAffiliateController {

    private final AffiliateService affiliateService;

    @GetMapping
    public List<PublicAffiliateProductResponse> listActive() {
        return affiliateService.listActive();
    }

    @GetMapping("/{id}/redirect")
    public ResponseEntity<Void> redirect(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionRef,
            Authentication authentication) {
        UUID authenticatedUserId = authentication != null ? (UUID) authentication.getPrincipal() : null;
        String redirectUrl = affiliateService.recordClickAndGetRedirectUrl(id, sessionRef, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}

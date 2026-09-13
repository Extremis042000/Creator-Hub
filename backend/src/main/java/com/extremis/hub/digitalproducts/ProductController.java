package com.extremis.hub.digitalproducts;

import com.extremis.hub.admin.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final DigitalProductService digitalProductService;

    @GetMapping
    public List<PublicProductResponse> listActive() {
        return digitalProductService.listActive();
    }

    @GetMapping("/owned")
    public List<UUID> listOwnedProductIds(Authentication authentication) {
        return digitalProductService.listOwnedProductIds(requireUserId(authentication));
    }

    @PostMapping("/{id}/test-purchase")
    public TestPurchaseResponse testPurchase(@PathVariable UUID id, Authentication authentication) {
        return digitalProductService.testPurchase(requireUserId(authentication), id);
    }

    @GetMapping("/{id}/download")
    public DownloadLinkResponse getDownloadLink(@PathVariable UUID id, Authentication authentication) {
        String url = digitalProductService.generateDownloadUrl(requireUserId(authentication), id);
        return new DownloadLinkResponse(url);
    }

    private UUID requireUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthenticatedException("Sign in required.");
        }
        return (UUID) authentication.getPrincipal();
    }
}

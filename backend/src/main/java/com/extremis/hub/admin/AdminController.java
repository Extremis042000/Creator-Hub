package com.extremis.hub.admin;

import com.extremis.hub.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/tools")
    public List<AdminToolResponse> listTools(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listTools();
    }

    @PatchMapping("/tools/{id}")
    public AdminToolResponse updateTool(
            @PathVariable UUID id, @Valid @RequestBody AdminToolUpdateRequest request,
            Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.updateTool(id, request);
    }

    @GetMapping("/feature-flags")
    public List<AdminFeatureFlagResponse> listFeatureFlags(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listFeatureFlags();
    }

    @PutMapping("/feature-flags/{key}")
    public AdminFeatureFlagResponse upsertFeatureFlag(
            @PathVariable String key, @Valid @RequestBody AdminFeatureFlagUpsertRequest request,
            Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.upsertFeatureFlag(key, request);
    }

    @GetMapping("/products")
    public List<AdminProductResponse> listProducts(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listProducts();
    }

    @PostMapping("/products")
    public AdminProductResponse createProduct(
            @Valid @RequestBody AdminProductRequest request, Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.createProduct(request);
    }

    @PatchMapping("/products/{id}")
    public AdminProductResponse updateProduct(
            @PathVariable UUID id, @Valid @RequestBody AdminProductUpdateRequest request,
            Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.updateProduct(id, request);
    }

    @GetMapping("/affiliate-products")
    public List<AdminAffiliateProductResponse> listAffiliateProducts(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listAffiliateProducts();
    }

    @PostMapping("/affiliate-products")
    public AdminAffiliateProductResponse createAffiliateProduct(
            @Valid @RequestBody AdminAffiliateProductRequest request, Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.createAffiliateProduct(request);
    }

    @PatchMapping("/affiliate-products/{id}")
    public AdminAffiliateProductResponse updateAffiliateProduct(
            @PathVariable UUID id, @Valid @RequestBody AdminAffiliateProductUpdateRequest request,
            Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.updateAffiliateProduct(id, request);
    }

    @GetMapping("/admins")
    public List<AdminUserSummary> listAdmins(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listAdmins();
    }

    @PostMapping("/admins")
    public ResponseEntity<Void> grantAdmin(
            @Valid @RequestBody GrantAdminRequest request, Authentication authentication) {
        User granter = adminService.requireAdmin(authentication);
        adminService.grantAdmin(granter.getId(), request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{userId}")
    public ResponseEntity<Void> revokeAdmin(@PathVariable UUID userId, Authentication authentication) {
        adminService.requireAdmin(authentication);
        adminService.revokeAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/premium-grants")
    public List<AdminPremiumUserResponse> listPremiumUsers(Authentication authentication) {
        adminService.requireAdmin(authentication);
        return adminService.listPremiumUsers();
    }

    @PostMapping("/premium-grants")
    public ResponseEntity<Void> grantPremium(
            @Valid @RequestBody GrantPremiumRequest request, Authentication authentication) {
        adminService.requireAdmin(authentication);
        adminService.grantPremium(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/premium-grants/{userId}")
    public ResponseEntity<Void> revokePremium(@PathVariable UUID userId, Authentication authentication) {
        adminService.requireAdmin(authentication);
        adminService.revokePremium(userId);
        return ResponseEntity.noContent().build();
    }
}

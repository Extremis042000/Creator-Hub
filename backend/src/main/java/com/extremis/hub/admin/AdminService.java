package com.extremis.hub.admin;

import com.extremis.hub.domain.AdminGrant;
import com.extremis.hub.domain.AffiliateProduct;
import com.extremis.hub.domain.FeatureFlag;
import com.extremis.hub.domain.Product;
import com.extremis.hub.domain.ProductCategory;
import com.extremis.hub.domain.Profile;
import com.extremis.hub.domain.Tool;
import com.extremis.hub.domain.User;
import com.extremis.hub.repository.AdminGrantRepository;
import com.extremis.hub.repository.AffiliateProductRepository;
import com.extremis.hub.repository.FeatureFlagRepository;
import com.extremis.hub.repository.ProductCategoryRepository;
import com.extremis.hub.repository.ProductRepository;
import com.extremis.hub.repository.ProfileRepository;
import com.extremis.hub.repository.ToolRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.web.BusinessRuleViolationException;
import com.extremis.hub.web.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the tool and feature_flag tables, plus who else has admin
 * access -- Product and AffiliateProduct don't exist yet (Phases
 * 18/19/22). See docs/03-development-roadmap.md Phase 17.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminAccessProperties adminAccessProperties;
    private final AdminAccessService adminAccessService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ToolRepository toolRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final AdminGrantRepository adminGrantRepository;
    private final AffiliateProductRepository affiliateProductRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;

    /**
     * Every admin endpoint calls this first. Deliberately mirrors the
     * manual-check style already used in AuthController rather than
     * introducing @PreAuthorize/roles that appear nowhere else in
     * this codebase. Returns the resolved User so callers that need
     * it (e.g. grantAdmin, to record who granted) don't look it up
     * twice.
     */
    public User requireAdmin(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthenticatedException("Sign in required.");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthenticatedException("Sign in required."));
        if (!adminAccessService.isAdmin(user)) {
            throw new ForbiddenException("Admin access required.");
        }
        return user;
    }

    public List<AdminToolResponse> listTools() {
        return toolRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminToolResponse updateTool(UUID id, AdminToolUpdateRequest request) {
        Tool tool = toolRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tool not found."));
        tool.setName(request.getName());
        tool.setCategory(request.getCategory());
        tool.setPremiumOnly(request.isPremiumOnly());
        toolRepository.save(tool);
        return toResponse(tool);
    }

    public List<AdminFeatureFlagResponse> listFeatureFlags() {
        return featureFlagRepository.findAll().stream().map(this::toResponse).toList();
    }

    /** Upsert by key -- no pre-seeding required to start toggling a flag. */
    @Transactional
    public AdminFeatureFlagResponse upsertFeatureFlag(String key, AdminFeatureFlagUpsertRequest request) {
        FeatureFlag flag = featureFlagRepository.findById(key).orElseGet(() -> {
            FeatureFlag newFlag = new FeatureFlag();
            newFlag.setKey(key);
            return newFlag;
        });
        flag.setEnabled(request.isEnabled());
        flag.setRolloutPercent(request.getRolloutPercent());
        featureFlagRepository.save(flag);
        return toResponse(flag);
    }

    public List<AdminAffiliateProductResponse> listAffiliateProducts() {
        return affiliateProductRepository.findAll().stream().map(this::toResponse).toList();
    }

    /** New products always start active=true -- there's no reason to create one already hidden. */
    @Transactional
    public AdminAffiliateProductResponse createAffiliateProduct(AdminAffiliateProductRequest request) {
        AffiliateProduct product = new AffiliateProduct();
        applyRequest(product, request);
        affiliateProductRepository.save(product);
        return toResponse(product);
    }

    @Transactional
    public AdminAffiliateProductResponse updateAffiliateProduct(UUID id, AdminAffiliateProductUpdateRequest request) {
        AffiliateProduct product = affiliateProductRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Affiliate product not found."));
        applyRequest(product, request);
        product.setActive(request.isActive());
        affiliateProductRepository.save(product);
        return toResponse(product);
    }

    private void applyRequest(AffiliateProduct product, AdminAffiliateProductRequest request) {
        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setCategory(request.getCategory());
        product.setPriceInfo(request.getPriceInfo());
        product.setAffiliateUrl(request.getAffiliateUrl());
        product.setMerchant(request.getMerchant());
        product.setRegion(request.getRegion());
        product.setDisclosureText(request.getDisclosureText());
    }

    public List<AdminProductResponse> listProducts() {
        return productRepository.findAll().stream()
            .filter(p -> p.getDeletedAt() == null)
            .map(this::toResponse)
            .toList();
    }

    /** New products always start active=true -- there's no reason to create one already hidden. */
    @Transactional
    public AdminProductResponse createProduct(AdminProductRequest request) {
        Product product = new Product();
        product.setCategory(resolveCategory(request.getCategorySlug()));
        applyRequest(product, request);
        productRepository.save(product);
        return toResponse(product);
    }

    @Transactional
    public AdminProductResponse updateProduct(UUID id, AdminProductUpdateRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        product.setCategory(resolveCategory(request.getCategorySlug()));
        applyRequest(product, request);
        product.setActive(request.isActive());
        productRepository.save(product);
        return toResponse(product);
    }

    private void applyRequest(Product product, AdminProductRequest request) {
        product.setName(request.getName());
        product.setPriceCents(request.getPriceCents());
        product.setCurrency(request.getCurrency() != null && !request.getCurrency().isBlank()
            ? request.getCurrency() : "USD");
        product.setFileRef(request.getFileRef());
    }

    /** Upsert by slug -- lets an admin type a new category freeform without a separate "create category" step. */
    private ProductCategory resolveCategory(String slug) {
        return productCategoryRepository.findBySlug(slug).orElseGet(() -> {
            ProductCategory category = new ProductCategory();
            category.setSlug(slug);
            category.setName(slug);
            return productCategoryRepository.save(category);
        });
    }

    /**
     * Founder allowlist entries first (even ones who've never signed
     * in -- shown with a null userId so the founder can see who's
     * configured), then everyone granted at runtime, deduplicated by
     * email in case an env-listed founder was also (redundantly)
     * granted in the DB.
     */
    public List<AdminUserSummary> listAdmins() {
        List<AdminUserSummary> result = new ArrayList<>();
        List<String> envEmails = adminAccessProperties.getEmails();

        for (String email : envEmails) {
            Optional<User> user = userRepository.findByEmail(email);
            result.add(AdminUserSummary.builder()
                .userId(user.map(User::getId).orElse(null))
                .email(email)
                .displayName(user.map(this::displayNameFor).orElse(null))
                .source(AdminUserSummary.AdminSource.ENV)
                .build());
        }

        for (AdminGrant grant : adminGrantRepository.findAll()) {
            userRepository.findById(grant.getUserId()).ifPresent(user -> {
                boolean alreadyListedViaEnv = envEmails.stream().anyMatch(e -> e.equalsIgnoreCase(user.getEmail()));
                if (!alreadyListedViaEnv) {
                    result.add(AdminUserSummary.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .displayName(displayNameFor(user))
                        .source(AdminUserSummary.AdminSource.GRANTED)
                        .build());
                }
            });
        }

        return result;
    }

    /**
     * The target must have signed in at least once (a real app_user
     * row) -- admin access can't be pre-granted to an email that's
     * never authenticated, since there'd be no user row to attach it
     * to. Idempotent: granting an already-admin user just confirms
     * their existing access rather than erroring.
     */
    @Transactional
    public void grantAdmin(UUID granterUserId, String email) {
        User target = userRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "That user must sign in at least once before being granted admin access."));

        if (adminAccessProperties.isAdmin(target.getEmail())) {
            throw new BusinessRuleViolationException(
                "This user is already an admin via the founder allowlist.");
        }
        if (adminGrantRepository.existsById(target.getId())) {
            return; // already granted -- idempotent, not an error
        }

        AdminGrant grant = new AdminGrant();
        grant.setUserId(target.getId());
        grant.setGrantedByUserId(granterUserId);
        adminGrantRepository.save(grant);
    }

    /** Only DB-granted admins can be revoked here -- ADMIN_EMAILS entries require an env-var change. */
    @Transactional
    public void revokeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (adminAccessProperties.isAdmin(user.getEmail())) {
            throw new BusinessRuleViolationException(
                "This admin is granted via the founder allowlist (ADMIN_EMAILS) and can't be revoked here.");
        }
        if (!adminGrantRepository.existsById(userId)) {
            throw new ResourceNotFoundException("This user is not an admin.");
        }
        adminGrantRepository.deleteById(userId);
    }

    private String displayNameFor(User user) {
        return profileRepository.findByUserId(user.getId()).map(Profile::getDisplayName).orElse(null);
    }

    private AdminToolResponse toResponse(Tool tool) {
        return AdminToolResponse.builder()
            .id(tool.getId())
            .toolType(tool.getToolType())
            .slug(tool.getSlug())
            .name(tool.getName())
            .category(tool.getCategory())
            .premiumOnly(tool.isPremiumOnly())
            .build();
    }

    private AdminFeatureFlagResponse toResponse(FeatureFlag flag) {
        return AdminFeatureFlagResponse.builder()
            .key(flag.getKey())
            .enabled(flag.isEnabled())
            .rolloutPercent(flag.getRolloutPercent())
            .build();
    }

    private AdminAffiliateProductResponse toResponse(AffiliateProduct product) {
        return AdminAffiliateProductResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .brand(product.getBrand())
            .category(product.getCategory())
            .priceInfo(product.getPriceInfo())
            .affiliateUrl(product.getAffiliateUrl())
            .merchant(product.getMerchant())
            .region(product.getRegion())
            .disclosureText(product.getDisclosureText())
            .active(product.isActive())
            .build();
    }

    private AdminProductResponse toResponse(Product product) {
        return AdminProductResponse.builder()
            .id(product.getId())
            .categorySlug(product.getCategory().getSlug())
            .name(product.getName())
            .priceCents(product.getPriceCents())
            .currency(product.getCurrency())
            .fileRef(product.getFileRef())
            .active(product.isActive())
            .build();
    }
}

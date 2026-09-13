package com.extremis.hub.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.AdminGrant;
import com.extremis.hub.domain.FeatureFlag;
import com.extremis.hub.domain.Tool;
import com.extremis.hub.domain.ToolType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private ToolRepository toolRepository;
    @Mock private FeatureFlagRepository featureFlagRepository;
    @Mock private AdminGrantRepository adminGrantRepository;
    @Mock private AffiliateProductRepository affiliateProductRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;

    private AdminAccessProperties adminAccessProperties;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminAccessProperties = new AdminAccessProperties();
        adminAccessProperties.setEmails(List.of("admin@example.com"));
        AdminAccessService adminAccessService = new AdminAccessService(adminAccessProperties, adminGrantRepository);
        adminService = new AdminService(
            adminAccessProperties, adminAccessService, userRepository, profileRepository, toolRepository,
            featureFlagRepository, adminGrantRepository, affiliateProductRepository, productRepository,
            productCategoryRepository);
    }

    private Authentication authFor(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void requireAdmin_throwsUnauthenticated_whenNoAuthentication() {
        assertThat(catchThrowable(() -> adminService.requireAdmin(null)))
            .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void requireAdmin_throwsForbidden_whenSignedInButNotAllowlistedOrGranted() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("player@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(adminGrantRepository.existsById(userId)).thenReturn(false);

        assertThat(catchThrowable(() -> adminService.requireAdmin(authFor(userId))))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireAdmin_succeeds_whenEmailIsAllowlisted_caseInsensitive() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("Admin@Example.com"); // different case than the allowlist entry
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        adminService.requireAdmin(authFor(userId)); // does not throw
    }

    @Test
    void requireAdmin_succeeds_whenGrantedInDatabase() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("granted@example.com"); // not in the env allowlist
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(adminGrantRepository.existsById(userId)).thenReturn(true);

        adminService.requireAdmin(authFor(userId)); // does not throw
    }

    @Test
    void updateTool_updatesEditableFieldsOnly() {
        UUID toolId = UUID.randomUUID();
        Tool tool = new Tool();
        tool.setId(toolId);
        tool.setToolType(ToolType.KD_CALCULATOR);
        tool.setSlug("kd-calculator");
        tool.setName("Old Name");
        when(toolRepository.findById(toolId)).thenReturn(Optional.of(tool));
        when(toolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminToolUpdateRequest request = new AdminToolUpdateRequest();
        request.setName("New Name");
        request.setCategory("competitive");
        request.setPremiumOnly(true);

        AdminToolResponse response = adminService.updateTool(toolId, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.isPremiumOnly()).isTrue();
        assertThat(response.getSlug()).isEqualTo("kd-calculator"); // unchanged, structural
        assertThat(response.getToolType()).isEqualTo(ToolType.KD_CALCULATOR); // unchanged
    }

    @Test
    void upsertFeatureFlag_createsNewFlag_whenKeyDoesNotExist() {
        when(featureFlagRepository.findById("new-flag")).thenReturn(Optional.empty());
        when(featureFlagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminFeatureFlagUpsertRequest request = new AdminFeatureFlagUpsertRequest();
        request.setEnabled(true);
        request.setRolloutPercent(50);

        AdminFeatureFlagResponse response = adminService.upsertFeatureFlag("new-flag", request);

        assertThat(response.getKey()).isEqualTo("new-flag");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getRolloutPercent()).isEqualTo(50);
    }

    @Test
    void upsertFeatureFlag_updatesExistingFlag_whenKeyExists() {
        FeatureFlag existing = new FeatureFlag();
        existing.setKey("existing-flag");
        existing.setEnabled(false);
        existing.setRolloutPercent(0);
        when(featureFlagRepository.findById("existing-flag")).thenReturn(Optional.of(existing));
        when(featureFlagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminFeatureFlagUpsertRequest request = new AdminFeatureFlagUpsertRequest();
        request.setEnabled(true);
        request.setRolloutPercent(100);

        AdminFeatureFlagResponse response = adminService.upsertFeatureFlag("existing-flag", request);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getRolloutPercent()).isEqualTo(100);
    }

    @Test
    void grantAdmin_rejectsTargetWhoHasNeverSignedIn() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(catchThrowable(() -> adminService.grantAdmin(UUID.randomUUID(), "nobody@example.com")))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(adminGrantRepository, never()).save(any());
    }

    @Test
    void grantAdmin_rejectsTargetAlreadyAdminViaEnvAllowlist() {
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("admin@example.com"); // matches the env allowlist in setUp()
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(target));

        assertThat(catchThrowable(() -> adminService.grantAdmin(UUID.randomUUID(), "admin@example.com")))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(adminGrantRepository, never()).save(any());
    }

    @Test
    void grantAdmin_createsGrant_forExistingNonAdminUser() {
        UUID granterUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        User target = new User();
        target.setId(targetUserId);
        target.setEmail("newadmin@example.com");
        when(userRepository.findByEmail("newadmin@example.com")).thenReturn(Optional.of(target));
        when(adminGrantRepository.existsById(targetUserId)).thenReturn(false);
        when(adminGrantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.grantAdmin(granterUserId, "newadmin@example.com");

        verify(adminGrantRepository).save(argThatGrantMatches(targetUserId, granterUserId));
    }

    @Test
    void grantAdmin_isIdempotent_whenAlreadyGranted() {
        UUID targetUserId = UUID.randomUUID();
        User target = new User();
        target.setId(targetUserId);
        target.setEmail("alreadyadmin@example.com");
        when(userRepository.findByEmail("alreadyadmin@example.com")).thenReturn(Optional.of(target));
        when(adminGrantRepository.existsById(targetUserId)).thenReturn(true);

        adminService.grantAdmin(UUID.randomUUID(), "alreadyadmin@example.com"); // does not throw

        verify(adminGrantRepository, never()).save(any());
    }

    @Test
    void revokeAdmin_rejectsEnvAllowlistedAdmin() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("admin@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(catchThrowable(() -> adminService.revokeAdmin(userId)))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(adminGrantRepository, never()).deleteById(any());
    }

    @Test
    void revokeAdmin_rejectsUserWhoIsNotAnAdmin() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("player@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(adminGrantRepository.existsById(userId)).thenReturn(false);

        assertThat(catchThrowable(() -> adminService.revokeAdmin(userId)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revokeAdmin_deletesGrant_forDbGrantedAdmin() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("granted@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(adminGrantRepository.existsById(userId)).thenReturn(true);

        adminService.revokeAdmin(userId);

        verify(adminGrantRepository).deleteById(userId);
    }

    private AdminGrant argThatGrantMatches(UUID expectedUserId, UUID expectedGranterId) {
        return org.mockito.ArgumentMatchers.argThat(grant ->
            grant.getUserId().equals(expectedUserId) && grant.getGrantedByUserId().equals(expectedGranterId));
    }
}

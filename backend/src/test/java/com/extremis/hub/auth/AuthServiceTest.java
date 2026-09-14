package com.extremis.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.extremis.hub.admin.AdminAccessService;
import com.extremis.hub.domain.Profile;
import com.extremis.hub.domain.User;
import com.extremis.hub.premium.PremiumAccessService;
import com.extremis.hub.repository.ProfileRepository;
import com.extremis.hub.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private GoogleTokenVerifierService googleTokenVerifierService;
    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AdminAccessService adminAccessService;
    @Mock private PremiumAccessService premiumAccessService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-at-least-32-characters-long-for-hs256");
        JwtService jwtService = new JwtService(jwtProperties);
        jwtService.init();
        authService = new AuthService(
            googleTokenVerifierService, userRepository, profileRepository, jwtService, adminAccessService,
            premiumAccessService);
    }

    @Test
    void firstSignIn_createsNewUserAndProfile() {
        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "player@example.com", "Player One", null);
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.signInWithGoogle("valid-token");

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo("player@example.com");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Player One");
    }

    @Test
    void secondSignIn_reusesExistingUserByGoogleSubjectId() {
        UUID existingUserId = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setEmail("player@example.com");
        existingUser.setGoogleSubjectId("google-subject-123");

        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "player@example.com", "Player One", null);
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile existingProfile = new Profile();
        existingProfile.setUser(existingUser);
        existingProfile.setDisplayName("Player One");
        when(profileRepository.findByUserId(existingUserId)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.signInWithGoogle("valid-token");

        assertThat(response.getUser().getId()).isEqualTo(existingUserId);
        // No new user created -- findByEmail should never even be consulted
        // since the googleSubjectId lookup already found the user.
    }

    @Test
    void signInAfterAccountDeletion_clearsDeletedAt() {
        // Found during Phase 16 QA: signing back in with the same
        // Google account previously used to delete the account must
        // reactivate it cleanly, not leave a stale deletedAt on an
        // account that can now sign in and act normally.
        UUID existingUserId = UUID.randomUUID();
        User deletedUser = new User();
        deletedUser.setId(existingUserId);
        deletedUser.setEmail("player@example.com");
        deletedUser.setGoogleSubjectId("google-subject-123");
        deletedUser.setDeletedAt(Instant.now().minusSeconds(3600));

        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "player@example.com", "Player One", null);
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.of(deletedUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByUserId(existingUserId)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.signInWithGoogle("valid-token");

        assertThat(deletedUser.getDeletedAt()).isNull();
    }

    @Test
    void signIn_includesAvatarUrl_fromGooglePictureClaim() {
        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "player@example.com", "Player One", "https://example.com/photo.jpg");
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.signInWithGoogle("valid-token");

        assertThat(response.getUser().getAvatarUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void signIn_leavesAvatarUrlNull_whenGoogleOmitsPictureClaim() {
        // Google's picture claim can legitimately be absent -- must not
        // overwrite an existing avatar with null, and must not crash.
        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "player@example.com", "Player One", null);
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.signInWithGoogle("valid-token");

        assertThat(response.getUser().getAvatarUrl()).isNull();
    }

    @Test
    void signIn_includesAdminFlag_fromAdminAccessService() {
        var verified = new GoogleTokenVerifierService.VerifiedGoogleUser(
            "google-subject-123", "admin@example.com", "Admin Person", null);
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(Optional.of(verified));
        when(userRepository.findByGoogleSubjectId("google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(adminAccessService.isAdmin(any())).thenReturn(true);

        AuthResponse response = authService.signInWithGoogle("valid-token");

        assertThat(response.getUser().isAdminFlag()).isTrue();
    }

    @Test
    void invalidGoogleToken_throwsInvalidGoogleTokenException() {
        when(googleTokenVerifierService.verify("bad-token")).thenReturn(Optional.empty());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> authService.signInWithGoogle("bad-token")))
            .isInstanceOf(InvalidGoogleTokenException.class);
    }
}

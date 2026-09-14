package com.extremis.hub.auth;

import com.extremis.hub.admin.AdminAccessService;
import com.extremis.hub.domain.Profile;
import com.extremis.hub.domain.User;
import com.extremis.hub.premium.PremiumAccessService;
import com.extremis.hub.repository.ProfileRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.web.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * No app_user row is created for anonymous visitors -- a row is
 * first created here, at real Google sign-in, per
 * docs/07-phase2-system-design.md §1.1.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final JwtService jwtService;
    private final AdminAccessService adminAccessService;
    private final PremiumAccessService premiumAccessService;

    @Transactional
    public AuthResponse signInWithGoogle(String googleIdToken) {
        GoogleTokenVerifierService.VerifiedGoogleUser verified = googleTokenVerifierService.verify(googleIdToken)
            .orElseThrow(() -> new InvalidGoogleTokenException("Google sign-in could not be verified."));

        User user = userRepository.findByGoogleSubjectId(verified.subject())
            .or(() -> userRepository.findByEmail(verified.email()))
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(verified.email());
                return newUser;
            });
        user.setGoogleSubjectId(verified.subject());
        // Signing back in via a freshly-verified Google token is a
        // legitimate re-authentication, not account resurrection by an
        // attacker -- clear a stale deletedAt rather than leaving an
        // account that can sign in and act normally still marked
        // deleted (a real gap found during Phase 16 QA).
        user.setDeletedAt(null);
        userRepository.save(user);

        Profile profile = profileRepository.findByUserId(user.getId()).orElseGet(() -> {
            Profile newProfile = new Profile();
            newProfile.setUser(user);
            return newProfile;
        });
        if (verified.name() != null) {
            profile.setDisplayName(verified.name());
        }
        // Refreshed on every sign-in -- Google's own picture URL, not
        // copied into our own storage, so it stays current if the user
        // changes their Google avatar.
        if (verified.pictureUrl() != null) {
            profile.setAvatarUrl(verified.pictureUrl());
        }
        profileRepository.save(profile);

        String token = jwtService.issueToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
            .token(token)
            .user(CurrentUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .adminFlag(adminAccessService.isAdmin(user))
                .hasPremiumAccess(premiumAccessService.hasPremiumAccess(user.getId()))
                .build())
            .build();
    }

    public CurrentUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        return CurrentUserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .displayName(profile != null ? profile.getDisplayName() : null)
            .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
            .adminFlag(adminAccessService.isAdmin(user))
            .hasPremiumAccess(premiumAccessService.hasPremiumAccess(userId))
            .build();
    }

    /** Soft delete -- see docs/06-prd.md §9 (account deletion). */
    @Transactional
    public void deleteCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }
}

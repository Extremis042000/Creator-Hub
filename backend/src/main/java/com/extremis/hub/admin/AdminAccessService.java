package com.extremis.hub.admin;

import com.extremis.hub.domain.User;
import com.extremis.hub.repository.AdminGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "is this user an admin" -- combines the
 * ADMIN_EMAILS bootstrap allowlist with runtime-granted admins. Used
 * by both AdminService (to gate /api/v1/admin/**) and AuthService (so
 * GET /api/v1/auth/me can tell the frontend whether to show the
 * admin-link UI, without the frontend needing a separate call that
 * 401s/403s for every non-admin visitor).
 */
@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private final AdminAccessProperties adminAccessProperties;
    private final AdminGrantRepository adminGrantRepository;

    public boolean isAdmin(User user) {
        return adminAccessProperties.isAdmin(user.getEmail()) || adminGrantRepository.existsById(user.getId());
    }
}

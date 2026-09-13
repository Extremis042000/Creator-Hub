package com.extremis.hub.auth;

import com.extremis.hub.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads Authorization: Bearer <token>, and if it's a valid EXTREMIS
 * JWT for a user who is NOT soft-deleted, sets the authenticated
 * principal to the user's UUID. Absent or invalid tokens, or a token
 * for a deleted account, simply leave the request unauthenticated --
 * every MVP tool endpoint stays permitAll regardless (see
 * SecurityConfig); only /api/v1/auth/me requires this to have
 * succeeded. The deletedAt check exists because JWTs are stateless
 * (no server-side revocation list): without it, a still-valid token
 * issued before account deletion would keep working for up to 7 days
 * after DELETE /api/v1/auth/me -- found during Phase 16 QA.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Optional<UUID> userId = jwtService.validateAndGetUserId(token);
            boolean notDeleted = userId.isPresent()
                && userRepository.findById(userId.get())
                    .map(user -> user.getDeletedAt() == null)
                    .orElse(false);
            if (notDeleted && SecurityContextHolder.getContext().getAuthentication() == null) {
                var authentication =
                    new UsernamePasswordAuthenticationToken(userId.get(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}

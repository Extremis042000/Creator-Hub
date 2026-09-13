package com.extremis.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.User;
import com.extremis.hub.repository.UserRepository;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private UserRepository userRepository;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-at-least-32-characters-long-for-hs256");
        jwtService = new JwtService(jwtProperties);
        jwtService.init();
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenForActiveUser_setsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        User activeUser = new User();
        activeUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        String token = jwtService.issueToken(userId, "player@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    }

    @Test
    void validTokenForDeletedUser_doesNotSetAuthentication() throws Exception {
        // Found during Phase 16 QA: a JWT issued before account
        // deletion must stop working immediately, not remain valid
        // for its full 7-day lifetime.
        UUID userId = UUID.randomUUID();
        User deletedUser = new User();
        deletedUser.setId(userId);
        deletedUser.setDeletedAt(Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(deletedUser));

        String token = jwtService.issueToken(userId, "player@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noAuthorizationHeader_doesNotSetAuthentication() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private FilterChain noopChain() {
        return (req, res) -> { };
    }
}

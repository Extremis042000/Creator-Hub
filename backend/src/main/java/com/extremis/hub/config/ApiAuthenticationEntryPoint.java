package com.extremis.hub.config;

import com.extremis.hub.web.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Without this, a request rejected by SecurityConfig's
 * .authenticated() rule (e.g. /api/v1/admin/** with no token at all)
 * never reaches a controller, so GlobalExceptionHandler never runs --
 * Spring Security's own default entry point would return an
 * inconsistent (typically empty-body) response instead of the
 * standard ApiErrorResponse shape every other 4xx uses. This makes
 * that one path consistent with the rest of the API.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        ApiErrorResponse body = ApiErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("UNAUTHORIZED")
            .message("Sign in required.")
            .path(request.getRequestURI())
            .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        // Servlet's default response charset is ISO-8859-1 unless set
        // explicitly -- harmless for this ASCII message today, but
        // wrong, and every other JSON response in the API is UTF-8
        // (Spring's own converters default to it). Found during
        // Phase 17 QA while double-checking this endpoint's headers.
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

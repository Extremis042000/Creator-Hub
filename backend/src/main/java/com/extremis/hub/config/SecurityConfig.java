package com.extremis.hub.config;

import com.extremis.hub.auth.JwtAuthenticationFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * MVP tool endpoints stay fully unauthenticated (permitAll) --
 * Phase 16 only adds an OPTIONAL authenticated layer for
 * /api/v1/auth/me and future saved-history/premium routes. The
 * JwtAuthenticationFilter populates the SecurityContext when a valid
 * token is present; each controller decides for itself whether it
 * requires that (see AuthController's manual 401 checks) rather than
 * this config locking down routes globally -- keeps every anonymous
 * tool working exactly as before Phase 16.
 *
 * Phase 17 adds one exception: /api/v1/admin/** requires
 * authentication at this layer too, on top of AdminService's own
 * admin-email check -- defense in depth, not a replacement for it
 * (the admin-email allowlist check still runs in every admin
 * endpoint; this only guarantees an unauthenticated request never
 * reaches the controller at all).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JSON API, no session cookies
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(apiAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        // PATCH was missing here -- AdminController.updateTool uses
        // @PatchMapping, so the browser silently blocked every tool
        // edit at the CORS layer before it reached the backend at
        // all (found via founder QA on the live admin panel).
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

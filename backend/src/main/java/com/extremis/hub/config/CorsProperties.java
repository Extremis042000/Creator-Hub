package com.extremis.hub.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backed by extremis.cors.allowed-origins in application.yml. Never a
 * wildcard in production — see docs/06-prd.md §8.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.cors")
public class CorsProperties {
    private List<String> allowedOrigins = List.of("http://localhost:3000");
}

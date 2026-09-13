package com.extremis.hub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Classic Jackson 2 ObjectMapper for internal JSON handling (storing
 * tool input/output as JSONB in GeneratedResult -- see
 * SharedResultService -- and for ApiAuthenticationEntryPoint, which
 * writes a response body directly rather than returning from a
 * controller). Kept separate from whatever Spring MVC uses for actual
 * controller-return HTTP serialization; this bean is not registered
 * as an HttpMessageConverter.
 *
 * WRITE_DATES_AS_TIMESTAMPS disabled to match Spring Boot's own
 * auto-configured Jackson mapper (which disables it by default) --
 * without this, Instant fields serialize as raw numeric epoch values
 * instead of ISO-8601 strings, which is what every OTHER
 * ApiErrorResponse in the API actually renders (a real inconsistency
 * caught during Phase 17 QA on ApiAuthenticationEntryPoint's output).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

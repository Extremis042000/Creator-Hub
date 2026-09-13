package com.extremis.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAuthenticationEntryPointTest {

    @Test
    void writesIsoTimestamp_notRawEpochNumber() throws Exception {
        // Regression test for a bug found during Phase 17 QA: the
        // shared ObjectMapper bean serialized Instant as a numeric
        // epoch value here, inconsistent with every other
        // ApiErrorResponse in the API (ISO-8601 strings).
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tools");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, null);

        assertThat(response.getStatus()).isEqualTo(401);
        String body = response.getContentAsString();
        assertThat(body).contains("\"error\":\"UNAUTHORIZED\"");
        // ISO-8601 timestamps contain "T" and "Z"/offset; a raw epoch
        // number would not.
        assertThat(body).containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T");
    }
}

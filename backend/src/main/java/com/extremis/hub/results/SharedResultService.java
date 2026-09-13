package com.extremis.hub.results;

import com.extremis.hub.domain.GeneratedResult;
import com.extremis.hub.domain.ToolType;
import com.extremis.hub.domain.ToolUsage;
import com.extremis.hub.repository.GeneratedResultRepository;
import com.extremis.hub.repository.ToolUsageRepository;
import com.extremis.hub.web.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared by every tool controller for save=true handling — one
 * mechanism, not one per tool. See docs/06-prd.md §4/§4.1/§4.2.
 */
@Service
@RequiredArgsConstructor
public class SharedResultService {

    private static final int ANONYMOUS_RESULT_TTL_DAYS = 30;

    private final GeneratedResultRepository generatedResultRepository;
    private final ToolUsageRepository toolUsageRepository;
    private final ShareTokenGenerator shareTokenGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Writes both a GeneratedResult (shareable) and a ToolUsage
     * (analytics) row. Only ever called when save=true -- save=false
     * must never reach here, per the "zero database writes" guarantee
     * in docs/06-prd.md §4.
     */
    @Transactional
    @SneakyThrows
    public String save(ToolType toolType, Object inputDto, Object outputDto) {
        String shareToken = shareTokenGenerator.generate();

        GeneratedResult result = new GeneratedResult();
        result.setToolType(toolType);
        result.setInputJson(objectMapper.writeValueAsString(inputDto));
        result.setOutputJson(objectMapper.writeValueAsString(outputDto));
        result.setShareToken(shareToken);
        result.setExpiresAt(Instant.now().plus(ANONYMOUS_RESULT_TTL_DAYS, ChronoUnit.DAYS));
        generatedResultRepository.save(result);

        ToolUsage usage = new ToolUsage();
        usage.setToolType(toolType);
        usage.setInputSummary(objectMapper.writeValueAsString(inputDto));
        toolUsageRepository.save(usage);

        return shareToken;
    }

    @SneakyThrows
    public SharedResultResponse getByShareToken(String shareToken) {
        GeneratedResult result = generatedResultRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new ResourceNotFoundException("Result not found."));

        // Expiry is the source of truth at read time, independent of
        // whether the scheduled cleanup job has purged the row yet —
        // see docs/06-prd.md §4.1.
        if (Instant.now().isAfter(result.getExpiresAt())) {
            throw new ResourceNotFoundException("Result not found.");
        }

        Map<String, Object> resultMap =
            objectMapper.readValue(result.getOutputJson(), new TypeReference<Map<String, Object>>() {});
        return SharedResultResponse.builder()
            .toolType(result.getToolType())
            .result(resultMap)
            .createdAt(result.getCreatedAt())
            .expiresAt(result.getExpiresAt())
            .build();
    }
}

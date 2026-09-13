package com.extremis.hub.featureflags;

import com.extremis.hub.domain.FeatureFlag;
import com.extremis.hub.repository.FeatureFlagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enabled feature flags double as "coming soon" tool cards on /tools --
 * rolloutPercent is a backend rollout control, not a public visibility
 * gate, so it's intentionally left out of this response.
 */
@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
public class PublicFeatureFlagController {

    private final FeatureFlagRepository featureFlagRepository;

    @GetMapping
    public List<String> listEnabledFlagKeys() {
        return featureFlagRepository.findAll().stream()
                .filter(FeatureFlag::isEnabled)
                .map(FeatureFlag::getKey)
                .toList();
    }
}

package com.extremis.hub.tools.bgmi;

import com.extremis.hub.domain.BgmiConfidenceLevel;
import com.extremis.hub.domain.BgmiSensitivityProfile;
import com.extremis.hub.domain.BgmiSensitivityProfileId;
import com.extremis.hub.domain.GyroscopeUsage;
import com.extremis.hub.domain.ScopeLevel;
import com.extremis.hub.repository.BgmiSensitivityProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Data-driven lookup, deterministic — docs/06-prd.md §5.3. */
@Service
@RequiredArgsConstructor
public class BgmiSensitivityService {

    private static final String DISCLAIMER =
        "These are recommended starting-point settings based on your gyroscope usage and scope level. "
        + "Adjust them to your personal comfort and device performance.";

    // Fixed response order — docs/06-prd.md §5.3.
    private static final List<ScopeLevel> SCOPE_ORDER = List.of(
        ScopeLevel.NO_SCOPE, ScopeLevel.RED_DOT, ScopeLevel.SCOPE_3X,
        ScopeLevel.SCOPE_4X_ACOG, ScopeLevel.SNIPER_SCOPE);

    private final BgmiSensitivityProfileRepository profileRepository;

    public BgmiSensitivityResponse recommend(BgmiSensitivityRequest request) {
        GyroscopeUsage gyroscopeUsage =
            request.getUsesGyroscope() ? GyroscopeUsage.GYRO_ON : GyroscopeUsage.GYRO_OFF;

        List<ScopeRecommendation> recommendations = SCOPE_ORDER.stream()
            .map(scopeLevel -> recommendationFor(gyroscopeUsage, scopeLevel))
            .toList();

        return BgmiSensitivityResponse.builder()
            .recommendations(recommendations)
            .disclaimer(DISCLAIMER)
            .build();
    }

    private ScopeRecommendation recommendationFor(GyroscopeUsage gyroscopeUsage, ScopeLevel scopeLevel) {
        String settingName = settingNameFor(gyroscopeUsage, scopeLevel);
        var id = new BgmiSensitivityProfileId(gyroscopeUsage, scopeLevel);

        return profileRepository.findById(id)
            .filter(BgmiSensitivityProfile::isActive)
            .map(row -> ScopeRecommendation.builder()
                .scopeLevel(scopeLevel)
                .settingName(settingName)
                .recommendedMin(row.getRecommendedMin())
                .recommendedMax(row.getRecommendedMax())
                .recommendedStartingValue(row.getRecommendedStartingValue())
                .confidence(row.getConfidence())
                .build())
            // DB-to-API transformation rule: inactive (or missing)
            // rows always surface as INSUFFICIENT_DATA with null
            // ranges, regardless of what confidence is stored
            // internally -- approval state is never leaked to the
            // API. See docs/06-prd.md §5.3.
            .orElseGet(() -> ScopeRecommendation.builder()
                .scopeLevel(scopeLevel)
                .settingName(settingName)
                .confidence(BgmiConfidenceLevel.INSUFFICIENT_DATA)
                .build());
    }

    private String settingNameFor(GyroscopeUsage gyroscopeUsage, ScopeLevel scopeLevel) {
        if (gyroscopeUsage == GyroscopeUsage.GYRO_ON) {
            return "Gyroscope Sensitivity";
        }
        return scopeLevel == ScopeLevel.NO_SCOPE ? "Camera Sensitivity" : "ADS Sensitivity";
    }
}

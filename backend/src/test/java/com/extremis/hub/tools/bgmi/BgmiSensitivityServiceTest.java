package com.extremis.hub.tools.bgmi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.BgmiConfidenceLevel;
import com.extremis.hub.domain.BgmiSensitivityProfile;
import com.extremis.hub.domain.BgmiSensitivityProfileId;
import com.extremis.hub.domain.GyroscopeUsage;
import com.extremis.hub.domain.ScopeLevel;
import com.extremis.hub.repository.BgmiSensitivityProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BgmiSensitivityServiceTest {

    @Mock
    private BgmiSensitivityProfileRepository profileRepository;

    @Test
    void returnsFiveRowsInFixedOrder_withGyroSettingName() {
        // No stubbing -- every lookup misses -> INSUFFICIENT_DATA, which
        // still exercises the fixed-order/settingName logic end-to-end.
        BgmiSensitivityService service = new BgmiSensitivityService(profileRepository);
        BgmiSensitivityRequest request = new BgmiSensitivityRequest();
        request.setUsesGyroscope(true);

        BgmiSensitivityResponse response = service.recommend(request);

        assertThat(response.getRecommendations()).extracting("scopeLevel")
            .containsExactly(
                ScopeLevel.NO_SCOPE, ScopeLevel.RED_DOT, ScopeLevel.SCOPE_3X,
                ScopeLevel.SCOPE_4X_ACOG, ScopeLevel.SNIPER_SCOPE);
        assertThat(response.getRecommendations())
            .allMatch(r -> r.getSettingName().equals("Gyroscope Sensitivity"));
        assertThat(response.getDisclaimer()).contains("gyroscope usage and scope level");
    }

    @Test
    void nonGyroscope_usesCameraSensitivityForNoScope_adsForOthers() {
        BgmiSensitivityService service = new BgmiSensitivityService(profileRepository);
        BgmiSensitivityRequest request = new BgmiSensitivityRequest();
        request.setUsesGyroscope(false);

        BgmiSensitivityResponse response = service.recommend(request);

        assertThat(response.getRecommendations().get(0).getSettingName()).isEqualTo("Camera Sensitivity");
        assertThat(response.getRecommendations().subList(1, 5))
            .allMatch(r -> r.getSettingName().equals("ADS Sensitivity"));
    }

    @Test
    void activeRow_returnsRealRangeAndConfidence() {
        BgmiSensitivityProfile row = new BgmiSensitivityProfile();
        row.setGyroscopeUsage(GyroscopeUsage.GYRO_ON);
        row.setScopeLevel(ScopeLevel.NO_SCOPE);
        row.setRecommendedMin(300);
        row.setRecommendedMax(400);
        row.setRecommendedStartingValue(350);
        row.setConfidence(BgmiConfidenceLevel.MEDIUM);
        row.setActive(true);
        when(profileRepository.findById(new BgmiSensitivityProfileId(GyroscopeUsage.GYRO_ON, ScopeLevel.NO_SCOPE)))
            .thenReturn(Optional.of(row));

        BgmiSensitivityService service = new BgmiSensitivityService(profileRepository);
        BgmiSensitivityRequest request = new BgmiSensitivityRequest();
        request.setUsesGyroscope(true);

        ScopeRecommendation noScope = service.recommend(request).getRecommendations().get(0);
        assertThat(noScope.getRecommendedMin()).isEqualTo(300);
        assertThat(noScope.getRecommendedMax()).isEqualTo(400);
        assertThat(noScope.getConfidence()).isEqualTo(BgmiConfidenceLevel.MEDIUM);
    }

    @Test
    void inactiveRow_surfacesAsInsufficientData_evenIfConfidenceStoredAsMedium() {
        BgmiSensitivityProfile row = new BgmiSensitivityProfile();
        row.setGyroscopeUsage(GyroscopeUsage.GYRO_ON);
        row.setScopeLevel(ScopeLevel.NO_SCOPE);
        row.setRecommendedMin(300);
        row.setRecommendedMax(400);
        row.setConfidence(BgmiConfidenceLevel.MEDIUM); // researched...
        row.setActive(false); // ...but not approved
        when(profileRepository.findById(new BgmiSensitivityProfileId(GyroscopeUsage.GYRO_ON, ScopeLevel.NO_SCOPE)))
            .thenReturn(Optional.of(row));

        BgmiSensitivityService service = new BgmiSensitivityService(profileRepository);
        BgmiSensitivityRequest request = new BgmiSensitivityRequest();
        request.setUsesGyroscope(true);

        ScopeRecommendation noScope = service.recommend(request).getRecommendations().get(0);
        assertThat(noScope.getConfidence()).isEqualTo(BgmiConfidenceLevel.INSUFFICIENT_DATA);
        assertThat(noScope.getRecommendedMin()).isNull();
        assertThat(noScope.getRecommendedMax()).isNull();
    }
}

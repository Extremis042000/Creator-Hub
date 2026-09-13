package com.extremis.hub.tools.sensitivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.extremis.hub.domain.SensitivityGameProfile;
import com.extremis.hub.domain.SupportedGame;
import com.extremis.hub.repository.SensitivityGameProfileRepository;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensitivityConverterServiceTest {

    @Mock
    private SensitivityGameProfileRepository profileRepository;

    private SensitivityConverterService service;

    @BeforeEach
    void setUp() {
        service = new SensitivityConverterService(profileRepository);
    }

    private SensitivityGameProfile activeProfile(SupportedGame game, double yaw, String formulaVersion) {
        SensitivityGameProfile profile = new SensitivityGameProfile();
        profile.setGame(game);
        profile.setYawConstant(yaw);
        profile.setFormulaVersion(formulaVersion);
        profile.setActive(true);
        return profile;
    }

    private SensitivityConversionRequest request(
            SupportedGame source, SupportedGame target, int dpi, double sensitivity) {
        SensitivityConversionRequest r = new SensitivityConversionRequest();
        r.setSourceGame(source);
        r.setTargetGame(target);
        r.setDpi(dpi);
        r.setSourceSensitivity(sensitivity);
        return r;
    }

    @Test
    void csgoToValorant_800dpi_sens2_matchesVerifiedReferenceExample() {
        when(profileRepository.findById(SupportedGame.CSGO))
            .thenReturn(Optional.of(activeProfile(SupportedGame.CSGO, 0.022, "v1-cm360-ratio")));
        when(profileRepository.findById(SupportedGame.VALORANT))
            .thenReturn(Optional.of(activeProfile(SupportedGame.VALORANT, 0.07, "v1-cm360-ratio")));

        SensitivityConversionResponse response =
            service.convert(request(SupportedGame.CSGO, SupportedGame.VALORANT, 800, 2.0));

        // Reference values from docs/08-data-verification-report.md §3
        assertThat(response.getTargetSensitivity()).isEqualTo(0.6286);
        assertThat(response.getEffectiveDpi()).isEqualTo(1600.0);
        assertThat(response.getSourceCmPer360()).isCloseTo(25.9773, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getTargetCmPer360()).isCloseTo(25.9773, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void sameGame_returnsUnchanged_regardlessOfActiveFlag() {
        // No repository stub configured at all -- proves the profile
        // is never looked up for the same-game no-op path.
        SensitivityConversionResponse response =
            service.convert(request(SupportedGame.CSGO, SupportedGame.CSGO, 800, 1.5));

        assertThat(response.getTargetSensitivity()).isEqualTo(1.5);
        assertThat(response.getEffectiveDpi()).isEqualTo(1200.0);
        assertThat(response.getConversionNote()).isNotBlank();
    }

    @Test
    void rejectsConversion_whenTargetProfileInactive() {
        when(profileRepository.findById(SupportedGame.CSGO))
            .thenReturn(Optional.of(activeProfile(SupportedGame.CSGO, 0.022, "v1-cm360-ratio")));
        SensitivityGameProfile inactiveOverwatch = activeProfile(SupportedGame.OVERWATCH2, 0.0066, "v1-cm360-ratio");
        inactiveOverwatch.setActive(false);
        when(profileRepository.findById(SupportedGame.OVERWATCH2))
            .thenReturn(Optional.of(inactiveOverwatch));

        assertThatThrownBy(() -> service.convert(request(SupportedGame.CSGO, SupportedGame.OVERWATCH2, 800, 1.0)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}

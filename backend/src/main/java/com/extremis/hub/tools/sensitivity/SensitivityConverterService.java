package com.extremis.hub.tools.sensitivity;

import com.extremis.hub.domain.SensitivityGameProfile;
import com.extremis.hub.domain.SupportedGame;
import com.extremis.hub.repository.SensitivityGameProfileRepository;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Same-game precedence: the no-op check runs unconditionally BEFORE
 * the active-profile check, and never touches the repository -- see
 * docs/06-prd.md §5.2's same-game precedence rule.
 */
@Service
@RequiredArgsConstructor
public class SensitivityConverterService {

    private static final double CM_PER_INCH = 2.54;

    private final SensitivityGameProfileRepository profileRepository;

    public SensitivityConversionResponse convert(SensitivityConversionRequest request) {
        if (request.getSourceGame() == request.getTargetGame()) {
            return SensitivityConversionResponse.builder()
                .targetSensitivity(request.getSourceSensitivity())
                .effectiveDpi(request.getDpi() * request.getSourceSensitivity())
                .conversionNote("Source and target game are the same; sensitivity is unchanged.")
                .build();
        }

        SensitivityGameProfile source = requireActiveProfile(request.getSourceGame());
        SensitivityGameProfile target = requireActiveProfile(request.getTargetGame());

        if (!source.getFormulaVersion().equals(target.getFormulaVersion())) {
            throw new BusinessRuleViolationException("Conversion between these games is not supported yet.");
        }

        int dpi = request.getDpi();
        double sourceSensitivity = request.getSourceSensitivity();
        double effectiveDpi = dpi * sourceSensitivity;

        double sourceCm360 = cmPer360(dpi, sourceSensitivity, source.getYawConstant());
        // Computed from the raw, unrounded ratio -- never from the
        // already-rounded targetSensitivity below -- per the "don't
        // round intermediates" rule in docs/06-prd.md §7. Using the
        // rounded value here would make targetCm360 drift from
        // sourceCm360 by the rounding error, which is exactly the
        // kind of premature-rounding bug that rule exists to prevent.
        double rawTargetSensitivity = sourceSensitivity * (source.getYawConstant() / target.getYawConstant());
        double targetSensitivity = round(rawTargetSensitivity, 4);
        double targetCm360 = cmPer360(dpi, rawTargetSensitivity, target.getYawConstant());

        return SensitivityConversionResponse.builder()
            .targetSensitivity(targetSensitivity)
            .effectiveDpi(round(effectiveDpi, 4))
            .sourceCmPer360(round(sourceCm360, 4))
            .targetCmPer360(round(targetCm360, 4))
            .formulaVersion(source.getFormulaVersion())
            .build();
    }

    private SensitivityGameProfile requireActiveProfile(SupportedGame game) {
        Optional<SensitivityGameProfile> profile = profileRepository.findById(game);
        if (profile.isEmpty() || !profile.get().isActive()) {
            throw new BusinessRuleViolationException("Conversion between these games is not supported yet.");
        }
        return profile.get();
    }

    private double cmPer360(int dpi, double sensitivity, double yawConstant) {
        return (360.0 / (dpi * sensitivity * yawConstant)) * CM_PER_INCH;
    }

    private double round(double value, int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }
}

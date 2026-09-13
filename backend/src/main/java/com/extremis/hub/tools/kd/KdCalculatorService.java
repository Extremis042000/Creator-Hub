package com.extremis.hub.tools.kd;

import org.springframework.stereotype.Service;

/** Deterministic, no AI/network call — docs/06-prd.md §5.1. */
@Service
public class KdCalculatorService {

    public KdCalculatorResponse calculate(KdCalculatorRequest request) {
        int kills = request.getKills();
        int deaths = request.getDeaths();

        if (deaths == 0 && kills > 0) {
            return KdCalculatorResponse.builder()
                .displayValue("Perfect / No Deaths")
                .kills(kills)
                .deaths(deaths)
                .performanceCategory(PerformanceCategory.UNDEFEATED)
                .undefinedFlag(true)
                .build();
        }

        if (deaths == 0) { // and kills == 0
            return KdCalculatorResponse.builder()
                .displayValue("No Data")
                .performanceCategory(PerformanceCategory.NO_DATA)
                .undefinedFlag(true)
                .build();
        }

        double ratio = Math.round((kills / (double) deaths) * 100.0) / 100.0;
        return KdCalculatorResponse.builder()
            .kdRatio(ratio)
            .displayValue(String.format("%.2f", ratio))
            .performanceCategory(categoryFor(ratio))
            .undefinedFlag(false)
            .build();
    }

    private PerformanceCategory categoryFor(double ratio) {
        if (ratio >= 2.5) return PerformanceCategory.ELITE;
        if (ratio >= 1.5) return PerformanceCategory.STRONG;
        if (ratio >= 1.0) return PerformanceCategory.SOLID;
        return PerformanceCategory.DEVELOPING;
    }
}

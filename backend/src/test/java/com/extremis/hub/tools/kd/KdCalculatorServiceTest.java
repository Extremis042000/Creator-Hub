package com.extremis.hub.tools.kd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KdCalculatorServiceTest {

    private final KdCalculatorService service = new KdCalculatorService();

    private KdCalculatorRequest request(int kills, int deaths) {
        KdCalculatorRequest r = new KdCalculatorRequest();
        r.setKills(kills);
        r.setDeaths(deaths);
        return r;
    }

    @Test
    void strongCategory_forTwoToOneRatio() {
        KdCalculatorResponse response = service.calculate(request(20, 10));

        assertThat(response.getKdRatio()).isEqualTo(2.0);
        assertThat(response.getPerformanceCategory()).isEqualTo(PerformanceCategory.STRONG);
        assertThat(response.isUndefinedFlag()).isFalse();
    }

    @Test
    void undefined_whenDeathsZeroAndKillsPositive() {
        KdCalculatorResponse response = service.calculate(request(5, 0));

        assertThat(response.getKdRatio()).isNull();
        assertThat(response.getKills()).isEqualTo(5);
        assertThat(response.getDeaths()).isEqualTo(0);
        assertThat(response.getPerformanceCategory()).isEqualTo(PerformanceCategory.UNDEFEATED);
        assertThat(response.isUndefinedFlag()).isTrue();
        assertThat(response.getDisplayValue()).isEqualTo("Perfect / No Deaths");
    }

    @Test
    void noData_whenBothZero() {
        KdCalculatorResponse response = service.calculate(request(0, 0));

        assertThat(response.getKdRatio()).isNull();
        assertThat(response.getPerformanceCategory()).isEqualTo(PerformanceCategory.NO_DATA);
        assertThat(response.getDisplayValue()).isEqualTo("No Data");
    }

    @Test
    void categoryThresholds() {
        assertThat(service.calculate(request(9, 10)).getPerformanceCategory())
            .isEqualTo(PerformanceCategory.DEVELOPING); // 0.9
        assertThat(service.calculate(request(10, 10)).getPerformanceCategory())
            .isEqualTo(PerformanceCategory.SOLID); // 1.0
        assertThat(service.calculate(request(15, 10)).getPerformanceCategory())
            .isEqualTo(PerformanceCategory.STRONG); // 1.5
        assertThat(service.calculate(request(25, 10)).getPerformanceCategory())
            .isEqualTo(PerformanceCategory.ELITE); // 2.5
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        KdCalculatorResponse response = service.calculate(request(10, 3));
        assertThat(response.getKdRatio()).isEqualTo(3.33);
        assertThat(response.getDisplayValue()).isEqualTo("3.33");
    }
}

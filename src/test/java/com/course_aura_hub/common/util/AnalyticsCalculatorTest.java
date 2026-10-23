package com.course_aura_hub.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCalculatorTest {

    private final AnalyticsCalculator calculator = new AnalyticsCalculator();

    @Test
    void percentageChange_calculatesPositiveGrowth() {
        BigDecimal result = calculator.percentageChange(
                new BigDecimal("150.00"),
                new BigDecimal("100.00")
        );

        assertThat(result).isEqualByComparingTo("50.0");
    }

    @Test
    void percentageChange_handlesZeroPreviousValue() {
        BigDecimal result = calculator.percentageChange(
                new BigDecimal("150.00"),
                BigDecimal.ZERO
        );

        assertThat(result).isEqualByComparingTo("0.0");
    }

    @Test
    void percentageChange_calculatesNegativeGrowth() {
        BigDecimal result = calculator.percentageChange(
                new BigDecimal("75.00"),
                new BigDecimal("100.00")
        );

        assertThat(result).isEqualByComparingTo("-25.0");
    }

    @Test
    void average_handlesEmptyDataset() {
        assertThat(calculator.average(BigDecimal.TEN, 0, 2))
                .isEqualByComparingTo("0.00");
    }
}

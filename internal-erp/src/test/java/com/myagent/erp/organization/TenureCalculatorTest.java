package com.myagent.erp.organization;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenureCalculatorTest {

    private final TenureCalculator calculator = new TenureCalculator();

    @Test
    void anniversaryBoundaryIsCalculatedByCompletedYears() {
        assertThat(calculator.completedYears(LocalDate.of(2020, 9, 14), LocalDate.of(2026, 9, 13))).isEqualTo(5);
        assertThat(calculator.completedYears(LocalDate.of(2020, 9, 14), LocalDate.of(2026, 9, 14))).isEqualTo(6);
    }

    @Test
    void leapDayAnniversaryUsesLastValidDayInNonLeapYear() {
        assertThat(calculator.completedYears(LocalDate.of(2020, 2, 29), LocalDate.of(2025, 2, 27))).isEqualTo(4);
        assertThat(calculator.completedYears(LocalDate.of(2020, 2, 29), LocalDate.of(2025, 2, 28))).isEqualTo(5);
    }

    @Test
    void futureHireDateIsRejected() {
        assertThatThrownBy(() -> calculator.completedYears(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 9, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

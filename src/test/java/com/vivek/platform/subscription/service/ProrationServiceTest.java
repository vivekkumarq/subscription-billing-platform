package com.vivek.platform.subscription.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proration is pure arithmetic over a calendar, so it is pinned down with exact expected
 * amounts rather than tolerances.
 */
class ProrationServiceTest {

    private final ProrationService prorationService = new ProrationService();

    private static final BigDecimal BASIC = new BigDecimal("499.00");
    private static final BigDecimal PRO = new BigDecimal("1999.00");

    @Test
    @DisplayName("upgrading on the 1st prorates the whole month")
    void upgradeOnFirstDay() {
        BillingPeriod period = BillingPeriod.of(2026, 4); // 30 days
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2026-04-01T09:00:00Z"), period);

        assertThat(result.remainingDays()).isEqualTo(30);
        assertThat(result.daysInPeriod()).isEqualTo(30);
        assertThat(result.credit()).isEqualByComparingTo("499.00");
        assertThat(result.charge()).isEqualByComparingTo("1999.00");
        assertThat(result.netAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.isUpgrade()).isTrue();
    }

    @Test
    @DisplayName("upgrading mid-cycle prorates by remaining days, inclusive of the change day")
    void upgradeMidCycle() {
        // April has 30 days; changing on the 16th leaves 15 days (16th..30th) = exactly half.
        BillingPeriod period = BillingPeriod.of(2026, 4);
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2026-04-16T12:00:00Z"), period);

        assertThat(result.remainingDays()).isEqualTo(15);
        assertThat(result.credit()).isEqualByComparingTo("249.50");
        assertThat(result.charge()).isEqualByComparingTo("999.50");
        assertThat(result.netAmount()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("downgrading mid-cycle produces a negative net, i.e. money back")
    void downgradeMidCycle() {
        BillingPeriod period = BillingPeriod.of(2026, 4);
        ProrationResult result = prorationService.calculate(
                PRO, BASIC, Instant.parse("2026-04-16T12:00:00Z"), period);

        assertThat(result.credit()).isEqualByComparingTo("999.50");
        assertThat(result.charge()).isEqualByComparingTo("249.50");
        assertThat(result.netAmount()).isEqualByComparingTo("-750.00");
        assertThat(result.isUpgrade()).isFalse();
    }

    @Test
    @DisplayName("changing on the last day of the month prorates a single day")
    void changeOnLastDay() {
        BillingPeriod period = BillingPeriod.of(2026, 2); // 28 days in 2026
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2026-02-28T23:00:00Z"), period);

        assertThat(result.daysInPeriod()).isEqualTo(28);
        assertThat(result.remainingDays()).isEqualTo(1);
        // 499.00 * 1/28 = 17.821... -> 17.82
        assertThat(result.credit()).isEqualByComparingTo("17.82");
        // 1999.00 * 1/28 = 71.392... -> 71.39
        assertThat(result.charge()).isEqualByComparingTo("71.39");
        assertThat(result.netAmount()).isEqualByComparingTo("53.57");
    }

    @Test
    @DisplayName("a leap February is 29 days, not 28")
    void leapYearFebruary() {
        BillingPeriod period = BillingPeriod.of(2028, 2);
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2028-02-15T00:00:00Z"), period);

        assertThat(result.daysInPeriod()).isEqualTo(29);
        assertThat(result.remainingDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("a change dated after the period leaves nothing to prorate")
    void changeAfterPeriod() {
        BillingPeriod period = BillingPeriod.of(2026, 4);
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2026-05-02T00:00:00Z"), period);

        assertThat(result.remainingDays()).isZero();
        assertThat(result.credit()).isEqualByComparingTo("0.00");
        assertThat(result.charge()).isEqualByComparingTo("0.00");
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    @DisplayName("moving from a free plan credits nothing but still charges the new plan")
    void upgradeFromFreePlan() {
        BillingPeriod period = BillingPeriod.of(2026, 4);
        ProrationResult result = prorationService.calculate(
                BigDecimal.ZERO, BASIC, Instant.parse("2026-04-16T00:00:00Z"), period);

        assertThat(result.credit()).isEqualByComparingTo("0.00");
        assertThat(result.charge()).isEqualByComparingTo("249.50");
        assertThat(result.netAmount()).isEqualByComparingTo("249.50");
    }

    @ParameterizedTest(name = "{0}-{1} has {2} days")
    @CsvSource({
            "2026, 1, 31",
            "2026, 2, 28",
            "2026, 4, 30",
            "2028, 2, 29"
    })
    @DisplayName("period length follows the real calendar")
    void periodLength(int year, int month, int expectedDays) {
        assertThat(BillingPeriod.of(year, month).lengthInDays()).isEqualTo(expectedDays);
    }

    @Test
    @DisplayName("every prorated amount is scaled to two decimal places")
    void amountsAreMoneyScaled() {
        BillingPeriod period = BillingPeriod.of(2026, 1); // 31 days, awkward divisor
        ProrationResult result = prorationService.calculate(
                BASIC, PRO, Instant.parse("2026-01-17T00:00:00Z"), period);

        assertThat(result.credit().scale()).isEqualTo(2);
        assertThat(result.charge().scale()).isEqualTo(2);
        assertThat(result.netAmount().scale()).isEqualTo(2);
    }
}

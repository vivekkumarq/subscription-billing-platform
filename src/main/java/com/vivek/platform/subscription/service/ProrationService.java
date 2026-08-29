package com.vivek.platform.subscription.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Day-based proration for mid-cycle plan changes.
 *
 * <p>The tenant is refunded the unused remainder of the plan they are leaving and charged the
 * matching remainder of the plan they are joining. The change day itself counts towards the new
 * plan, so a change on the 1st prorates the full month and a change on the last day prorates a
 * single day.</p>
 *
 * <p>This is a pure function of its arguments - no repositories, no clock - so it can be unit
 * tested exhaustively.</p>
 */
@Service
public class ProrationService {

    /** Intermediate scale, kept higher than the money scale so the daily rate is not lost. */
    private static final int RATIO_SCALE = 10;

    public ProrationResult calculate(BigDecimal outgoingMonthlyPrice,
                                     BigDecimal incomingMonthlyPrice,
                                     Instant changedAt,
                                     BillingPeriod period) {
        LocalDate changeDate = LocalDate.ofInstant(changedAt, ZoneOffset.UTC);
        int daysInPeriod = period.lengthInDays();
        int remainingDays = remainingDays(changeDate, period);

        BigDecimal ratio = BigDecimal.valueOf(remainingDays)
                .divide(BigDecimal.valueOf(daysInPeriod), RATIO_SCALE, RoundingMode.HALF_UP);

        BigDecimal credit = Money.round(nullToZero(outgoingMonthlyPrice).multiply(ratio));
        BigDecimal charge = Money.round(nullToZero(incomingMonthlyPrice).multiply(ratio));
        BigDecimal net = Money.round(charge.subtract(credit));

        return new ProrationResult(credit, charge, net, remainingDays, daysInPeriod);
    }

    /**
     * Days left in the cycle counting the change day itself. A change outside the period yields
     * the whole period (change before it) or nothing (change after it).
     */
    private int remainingDays(LocalDate changeDate, BillingPeriod period) {
        LocalDate periodStart = LocalDate.ofInstant(period.start(), ZoneOffset.UTC);
        LocalDate periodEndExclusive = LocalDate.ofInstant(period.end(), ZoneOffset.UTC);
        if (changeDate.isBefore(periodStart)) {
            return period.lengthInDays();
        }
        if (!changeDate.isBefore(periodEndExclusive)) {
            return 0;
        }
        return period.lengthInDays() - changeDate.getDayOfMonth() + 1;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

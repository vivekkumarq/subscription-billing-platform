package com.vivek.platform.subscription.service;

import java.math.BigDecimal;

/**
 * Outcome of a mid-cycle plan change.
 *
 * @param credit          unused portion of the outgoing plan, refunded (positive amount)
 * @param charge          remaining portion of the incoming plan, billed (positive amount)
 * @param netAmount       {@code charge - credit}; positive means the tenant owes more
 * @param remainingDays   days of the cycle still to run, inclusive of the change day
 * @param daysInPeriod    total days in the billing cycle
 */
public record ProrationResult(BigDecimal credit,
                              BigDecimal charge,
                              BigDecimal netAmount,
                              int remainingDays,
                              int daysInPeriod) {

    public boolean isUpgrade() {
        return netAmount.signum() > 0;
    }

    public boolean isNoOp() {
        return credit.signum() == 0 && charge.signum() == 0;
    }
}

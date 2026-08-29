package com.vivek.platform.subscription.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding policy for currency amounts, in one place so every total on an invoice is scaled
 * and rounded identically.
 */
public final class Money {

    /** Minor-unit scale for presented amounts. */
    public static final int SCALE = 2;

    /** Banker-free half-up rounding, matching how invoices are conventionally rounded. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** Rounds to the presentation scale. */
    public static BigDecimal round(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
            }
        }
        return round(total);
    }

    public static BigDecimal multiply(BigDecimal rate, long quantity) {
        if (rate == null || quantity == 0L) {
            return ZERO;
        }
        return round(rate.multiply(BigDecimal.valueOf(quantity)));
    }
}

package com.vivek.platform.subscription.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * A calendar-month billing period expressed as a half-open UTC instant range,
 * {@code [start, end)}.
 *
 * <p>The old inline calculation built the upper bound as
 * {@code lastDayOfMonth.atTime(23, 59, 59)} and used {@code BETWEEN}, which excluded every
 * usage event recorded in the final second of the month.</p>
 */
public record BillingPeriod(int year, int month, Instant start, Instant end) {

    public static BillingPeriod of(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12, was: " + month);
        }
        YearMonth ym = YearMonth.of(year, month);
        Instant start = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new BillingPeriod(year, month, start, end);
    }

    public static BillingPeriod containing(Instant moment) {
        LocalDate date = LocalDate.ofInstant(moment, ZoneOffset.UTC);
        return of(date.getYear(), date.getMonthValue());
    }

    public int lengthInDays() {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public String label() {
        return String.format("%04d-%02d", year, month);
    }
}

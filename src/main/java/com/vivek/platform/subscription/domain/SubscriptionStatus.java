package com.vivek.platform.subscription.domain;

/**
 * Lifecycle state of a subscription. Replaces the previous boolean {@code active} flag, which
 * could not express "cancelled, but still serving until the end of the paid period".
 */
public enum SubscriptionStatus {

    /** Billable and serving traffic. */
    ACTIVE,

    /** Cancellation requested; billable until the effective end date, then terminal. */
    CANCELLED,

    /** An invoice went unpaid. Still current for billing purposes but flagged for dunning. */
    PAST_DUE
}

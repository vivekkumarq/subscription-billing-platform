package com.vivek.platform.subscription.domain;

public enum InvoiceLineType {

    /** Flat recurring plan fee for the billing period. */
    BASE_SUBSCRIPTION,

    /** Units consumed beyond the plan allowance, priced at the overage rate. */
    OVERAGE,

    /** Refund of the unused remainder of a plan the tenant moved off mid-cycle. */
    PRORATION_CREDIT,

    /** Charge for the remainder of the cycle on a plan the tenant moved onto mid-cycle. */
    PRORATION_CHARGE
}

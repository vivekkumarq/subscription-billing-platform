package com.vivek.platform.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Externalised billing configuration. The overage rate used to be a {@code private static final
 * double 0.10} buried in the billing service, so changing a price meant a redeploy and every
 * plan was forced to share one rate.
 */
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    /** Fallback rate per overage unit, used when a plan does not define its own. */
    private BigDecimal defaultOverageRate = new BigDecimal("0.10");

    /** ISO-4217 code stamped on generated invoices. */
    private String currency = "USD";

    /** Percentages of the plan allowance at which a quota alert is emitted. */
    private List<Integer> quotaAlertThresholds = List.of(80, 100);

    public BigDecimal getDefaultOverageRate() {
        return defaultOverageRate;
    }

    public void setDefaultOverageRate(BigDecimal defaultOverageRate) {
        this.defaultOverageRate = defaultOverageRate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<Integer> getQuotaAlertThresholds() {
        return quotaAlertThresholds;
    }

    public void setQuotaAlertThresholds(List<Integer> quotaAlertThresholds) {
        this.quotaAlertThresholds = quotaAlertThresholds;
    }
}

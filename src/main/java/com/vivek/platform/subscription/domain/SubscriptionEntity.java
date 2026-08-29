package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions",
        indexes = @Index(name = "idx_subscriptions_org_status", columnList = "organization_id, status"))
public class SubscriptionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlanEntity plan;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    /** Effective end of service. Null while the subscription runs indefinitely. */
    @Column(name = "end_date")
    private Instant endDate;

    /** When the customer asked to cancel, which is not the same as when service stops. */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    public UUID getId() {
        return id;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationEntity organization) {
        this.organization = organization;
    }

    public PlanEntity getPlan() {
        return plan;
    }

    public void setPlan(PlanEntity plan) {
        this.plan = plan;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    /**
     * A subscription is billable while ACTIVE or PAST_DUE, and while a CANCELLED subscription
     * has not yet reached its effective end date.
     */
    public boolean isBillableAt(Instant moment) {
        if (status == SubscriptionStatus.CANCELLED) {
            return endDate != null && moment.isBefore(endDate);
        }
        return true;
    }
}

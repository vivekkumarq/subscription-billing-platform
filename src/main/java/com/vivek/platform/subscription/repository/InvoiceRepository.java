package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.InvoiceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    /**
     * Line items are fetched with the invoices in a single query. Serialising a page of
     * invoices without this graph issues one extra select per invoice.
     */
    @EntityGraph(attributePaths = {"lineItems", "organization"})
    List<InvoiceEntity> findByOrganizationIdOrderByPeriodYearDescPeriodMonthDesc(UUID organizationId);

    @EntityGraph(attributePaths = {"lineItems", "organization"})
    Optional<InvoiceEntity> findByOrganizationIdAndInvoiceNumber(UUID organizationId, String invoiceNumber);

    @EntityGraph(attributePaths = {"lineItems", "organization"})
    Optional<InvoiceEntity> findByOrganizationIdAndPeriodYearAndPeriodMonth(UUID organizationId,
                                                                           int periodYear,
                                                                           int periodMonth);

    boolean existsByInvoiceNumber(String invoiceNumber);
}

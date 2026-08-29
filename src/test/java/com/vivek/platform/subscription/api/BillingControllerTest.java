package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.TestFixtures;
import com.vivek.platform.subscription.config.SecurityConfig;
import com.vivek.platform.subscription.domain.*;
import com.vivek.platform.subscription.exception.GlobalExceptionHandler;
import com.vivek.platform.subscription.exception.NoActiveSubscriptionException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class BillingControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    /** Required by the resource-server chain; the jwt() post-processor bypasses real decoding. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("no bearer token is rejected with 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/invoices/{orgId}", ORG_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("a member of the organization can list its invoices")
    void memberCanListInvoices() throws Exception {
        when(billingService.listInvoices(ORG_ID)).thenReturn(List.of(sampleInvoice()));

        mockMvc.perform(get("/invoices/{orgId}", ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-202602-AB12CD"))
                .andExpect(jsonPath("$[0].totalAmount").value(749.00))
                .andExpect(jsonPath("$[0].lineItems.length()").value(1));
    }

    /**
     * The vulnerability this guard exists for: before it, any authenticated token could read
     * any organization's invoices simply by putting a different id in the path.
     */
    @Test
    @DisplayName("a caller cannot read another organization's invoices")
    void tenantIsolationOnList() throws Exception {
        mockMvc.perform(get("/invoices/{orgId}", OTHER_ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("a caller cannot generate an invoice for another organization")
    void tenantIsolationOnGenerate() throws Exception {
        mockMvc.perform(post("/invoices/{orgId}/generate", OTHER_ORG_ID)
                        .param("year", "2026")
                        .param("month", "2")
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("a caller cannot fetch another organization's invoice by number")
    void tenantIsolationOnGetByNumber() throws Exception {
        mockMvc.perform(get("/invoices/{orgId}/{number}", OTHER_ORG_ID, "INV-202602-AB12CD")
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("a token with no tenant claim at all reaches nothing - deny by default")
    void tokenWithoutTenantClaimIsDenied() throws Exception {
        mockMvc.perform(get("/invoices/{orgId}", ORG_ID).with(jwt()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("a platform admin may reach any organization")
    void platformAdminBypassesTenantCheck() throws Exception {
        when(billingService.listInvoices(OTHER_ORG_ID)).thenReturn(List.of());

        mockMvc.perform(get("/invoices/{orgId}", OTHER_ORG_ID)
                        .with(jwt().authorities(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority(TenantAccessGuard.PLATFORM_ADMIN_ROLE))))
                .andExpect(status().isOk());
        verify(billingService).listInvoices(OTHER_ORG_ID);
    }

    @Test
    @DisplayName("a multi-tenant token may reach every organization it claims")
    void multiTenantClaimIsHonoured() throws Exception {
        when(billingService.listInvoices(OTHER_ORG_ID)).thenReturn(List.of());

        mockMvc.perform(get("/invoices/{orgId}", OTHER_ORG_ID)
                        .with(jwt().jwt(builder -> builder.claim(TenantAccessGuard.ORGS_CLAIM,
                                List.of(ORG_ID.toString(), OTHER_ORG_ID.toString())))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("generating an invoice returns the persisted document")
    void generateInvoice() throws Exception {
        when(billingService.generateInvoice(eq(ORG_ID), anyInt(), anyInt())).thenReturn(sampleInvoice());

        mockMvc.perform(post("/invoices/{orgId}/generate", ORG_ID)
                        .param("year", "2026")
                        .param("month", "2")
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("BASIC"))
                .andExpect(jsonPath("$.usedUnits").value(12500))
                .andExpect(jsonPath("$.overageUnits").value(2500))
                .andExpect(jsonPath("$.baseAmount").value(499.00))
                .andExpect(jsonPath("$.overageAmount").value(250.00))
                .andExpect(jsonPath("$.totalAmount").value(749.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("an unknown organization surfaces as 404 with a problem body")
    void unknownOrganizationIsNotFound() throws Exception {
        when(billingService.listInvoices(ORG_ID)).thenThrow(ResourceNotFoundException.organization(ORG_ID));

        mockMvc.perform(get("/invoices/{orgId}", ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("billing an organization with no subscription surfaces as 409")
    void noSubscriptionIsConflict() throws Exception {
        when(billingService.generateInvoice(eq(ORG_ID), anyInt(), anyInt()))
                .thenThrow(new NoActiveSubscriptionException(ORG_ID));

        mockMvc.perform(post("/invoices/{orgId}/generate", ORG_ID)
                        .param("year", "2026")
                        .param("month", "2")
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("No active subscription"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tokenFor(UUID orgId) {
        return jwt().jwt(builder -> builder.claim(TenantAccessGuard.ORG_CLAIM, orgId.toString()));
    }

    private static InvoiceEntity sampleInvoice() {
        OrganizationEntity org = TestFixtures.organization(ORG_ID, "Acme Corp");
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setInvoiceNumber("INV-202602-AB12CD");
        invoice.setOrganization(org);
        invoice.setPeriodYear(2026);
        invoice.setPeriodMonth(2);
        invoice.setPeriodStart(Instant.parse("2026-02-01T00:00:00Z"));
        invoice.setPeriodEnd(Instant.parse("2026-03-01T00:00:00Z"));
        invoice.setPlanType(PlanType.BASIC);
        invoice.setIncludedUnits(10_000);
        invoice.setUsedUnits(12_500L);
        invoice.setOverageUnits(2_500L);
        invoice.setOverageRate(new BigDecimal("0.10"));
        invoice.setBaseAmount(new BigDecimal("499.00"));
        invoice.setOverageAmount(new BigDecimal("250.00"));
        invoice.setAdjustmentAmount(new BigDecimal("0.00"));
        invoice.setTotalAmount(new BigDecimal("749.00"));
        invoice.setCurrency("USD");
        invoice.setIssuedAt(Instant.parse("2026-03-01T00:05:00Z"));
        invoice.addLineItem(new InvoiceLineItemEntity(InvoiceLineType.BASE_SUBSCRIPTION,
                "BASIC plan, 2026-02", BigDecimal.ONE,
                new BigDecimal("499.00"), new BigDecimal("499.00")));
        return invoice;
    }
}

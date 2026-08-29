package com.vivek.platform.subscription.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.platform.subscription.TestFixtures;
import com.vivek.platform.subscription.config.SecurityConfig;
import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.PlanType;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.exception.GlobalExceptionHandler;
import com.vivek.platform.subscription.exception.InvalidSubscriptionStateException;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.BillingPeriod;
import com.vivek.platform.subscription.service.ProrationResult;
import com.vivek.platform.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class SubscriptionControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionService subscriptionService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("subscribing returns 201 with the resolved plan")
    void subscribe() throws Exception {
        when(subscriptionService.createSubscription(ORG_ID, PlanType.BASIC)).thenReturn(activeSubscription());

        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", ORG_ID, "planType", "BASIC")))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planType").value("BASIC"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.includedUnits").value(10000));
    }

    @Test
    @DisplayName("a caller cannot subscribe another organization to a plan")
    void cannotSubscribeAnotherTenant() throws Exception {
        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", OTHER_ORG_ID, "planType", "BASIC")))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("a missing planType is a 400 with field-level detail")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("organizationId", ORG_ID)))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.planType").exists());
    }

    @Test
    @DisplayName("changing plan reports the proration that was applied")
    void changePlanReturnsProration() throws Exception {
        SubscriptionEntity upgraded = activeSubscription();
        ProrationResult proration = new ProrationResult(
                new BigDecimal("249.50"), new BigDecimal("999.50"), new BigDecimal("750.00"), 15, 30);
        when(subscriptionService.changePlan(ORG_ID, PlanType.PRO)).thenReturn(
                new SubscriptionService.PlanChange(upgraded, proration, BillingPeriod.of(2026, 4)));

        mockMvc.perform(post("/subscriptions/{orgId}/change-plan", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("planType", "PRO")))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prorationCredit").value(249.50))
                .andExpect(jsonPath("$.prorationCharge").value(999.50))
                .andExpect(jsonPath("$.netAdjustment").value(750.00))
                .andExpect(jsonPath("$.remainingDaysInPeriod").value(15))
                .andExpect(jsonPath("$.appliedToPeriod").value("2026-04"));
    }

    @Test
    @DisplayName("changing to the plan already held is a 409")
    void changeToSamePlanIsConflict() throws Exception {
        when(subscriptionService.changePlan(ORG_ID, PlanType.BASIC))
                .thenThrow(new InvalidSubscriptionStateException("Subscription is already on plan BASIC"));

        mockMvc.perform(post("/subscriptions/{orgId}/change-plan", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("planType", "BASIC")))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid subscription state"));
    }

    @Test
    @DisplayName("cancelling without a body defaults the effective date")
    void cancelWithoutBody() throws Exception {
        SubscriptionEntity cancelled = activeSubscription();
        when(subscriptionService.cancel(eq(ORG_ID), isNull())).thenReturn(cancelled);

        mockMvc.perform(post("/subscriptions/{orgId}/cancel", ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isOk());
        verify(subscriptionService).cancel(ORG_ID, null);
    }

    @Test
    @DisplayName("a caller cannot cancel another organization's subscription")
    void cannotCancelAnotherTenant() throws Exception {
        mockMvc.perform(post("/subscriptions/{orgId}/cancel", OTHER_ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verify(subscriptionService, never()).cancel(any(), any());
    }

    @Test
    @DisplayName("a caller cannot read another organization's subscription history")
    void cannotReadAnotherTenantHistory() throws Exception {
        mockMvc.perform(get("/subscriptions/{orgId}/history", OTHER_ORG_ID).with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(subscriptionService);
    }

    private static RequestPostProcessor tokenFor(UUID orgId) {
        return jwt().jwt(builder -> builder.claim(TenantAccessGuard.ORG_CLAIM, orgId.toString()));
    }

    private static SubscriptionEntity activeSubscription() {
        OrganizationEntity org = TestFixtures.organization(ORG_ID, "Acme Corp");
        return TestFixtures.subscription(org,
                TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"));
    }
}

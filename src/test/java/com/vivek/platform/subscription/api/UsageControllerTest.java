package com.vivek.platform.subscription.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivek.platform.subscription.TestFixtures;
import com.vivek.platform.subscription.config.SecurityConfig;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.exception.GlobalExceptionHandler;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.messaging.UsageEventProducer;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsageController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class UsageControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsageEventProducer producer;
    @MockitoBean
    private OrganizationService organizationService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("recording usage is accepted asynchronously and echoes the idempotency key")
    void recordUsage() throws Exception {
        when(organizationService.getOrganization(ORG_ID))
                .thenReturn(TestFixtures.organization(ORG_ID, "Acme Corp"));

        mockMvc.perform(post("/usage-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", ORG_ID, "unitsConsumed", 50)))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").exists())
                .andExpect(jsonPath("$.unitsConsumed").value(50))
                .andExpect(jsonPath("$.topic").value("usage-recorded-topic"));

        ArgumentCaptor<UsageRecordedEvent> captor = ArgumentCaptor.forClass(UsageRecordedEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().getEventId()).isNotNull();
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(captor.getValue().getUnits()).isEqualTo(50);
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("a caller cannot report usage against another organization")
    void cannotRecordForAnotherTenant() throws Exception {
        mockMvc.perform(post("/usage-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", OTHER_ORG_ID, "unitsConsumed", 50)))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(producer);
    }

    /**
     * A null {@code unitsConsumed} used to slip past validation - the field carried
     * {@code @Min(1)} only, which passes for null - and blew up further down the pipeline.
     */
    @Test
    @DisplayName("a null unitsConsumed is rejected at the edge rather than downstream")
    void nullUnitsIsRejected() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("organizationId", ORG_ID.toString());
        body.put("unitsConsumed", null);

        mockMvc.perform(post("/usage-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.unitsConsumed").exists());
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("zero units is rejected")
    void zeroUnitsIsRejected() throws Exception {
        mockMvc.perform(post("/usage-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", ORG_ID, "unitsConsumed", 0)))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(producer);
    }

    /**
     * Publishing usage for an organization that does not exist would create a poison record that
     * can only fail in the consumer, so the tenant is resolved before anything is published.
     */
    @Test
    @DisplayName("usage for an unknown organization is rejected before publishing")
    void unknownOrganizationIsNotPublished() throws Exception {
        when(organizationService.getOrganization(ORG_ID))
                .thenThrow(ResourceNotFoundException.organization(ORG_ID));

        mockMvc.perform(post("/usage-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("organizationId", ORG_ID, "unitsConsumed", 5)))
                        .with(tokenFor(ORG_ID)))
                .andExpect(status().isNotFound());
        verify(producer, never()).publish(any());
    }

    private static RequestPostProcessor tokenFor(UUID orgId) {
        return jwt().jwt(builder -> builder.claim(TenantAccessGuard.ORG_CLAIM, orgId.toString()));
    }
}

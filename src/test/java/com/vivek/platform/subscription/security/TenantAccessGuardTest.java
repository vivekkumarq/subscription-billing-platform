package com.vivek.platform.subscription.security;

import com.vivek.platform.subscription.exception.TenantAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the tenant boundary itself, independent of any controller.
 */
class TenantAccessGuardTest {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TenantAccessGuard guard = new TenantAccessGuard();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a token claiming the organization is allowed through")
    void memberIsAllowed() {
        authenticate(jwtWith(Map.of(TenantAccessGuard.ORG_CLAIM, ORG_A.toString())));

        assertThatCode(() -> guard.assertCanAccess(ORG_A)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a token claiming a different organization is denied")
    void nonMemberIsDenied() {
        authenticate(jwtWith(Map.of(TenantAccessGuard.ORG_CLAIM, ORG_A.toString())));

        assertThatThrownBy(() -> guard.assertCanAccess(ORG_B))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessageContaining(ORG_B.toString());
    }

    @Test
    @DisplayName("a token with no tenant claim reaches nothing")
    void tokenWithoutClaimIsDenied() {
        authenticate(jwtWith(Map.of("sub", "someone")));

        assertThatThrownBy(() -> guard.assertCanAccess(ORG_A))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThat(guard.callerOrganizations()).isEmpty();
    }

    @Test
    @DisplayName("an unauthenticated context reaches nothing")
    void anonymousIsDenied() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> guard.assertCanAccess(ORG_A))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    @DisplayName("the org_ids list claim grants access to every organization it names")
    void multiTenantClaim() {
        authenticate(jwtWith(Map.of(TenantAccessGuard.ORGS_CLAIM,
                List.of(ORG_A.toString(), ORG_B.toString()))));

        assertThatCode(() -> guard.assertCanAccess(ORG_A)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanAccess(ORG_B)).doesNotThrowAnyException();
        assertThat(guard.callerOrganizations()).containsExactlyInAnyOrder(ORG_A, ORG_B);
    }

    @Test
    @DisplayName("the platform admin role bypasses the tenant check")
    void platformAdminBypasses() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "admin", "n/a",
                List.of(new SimpleGrantedAuthority(TenantAccessGuard.PLATFORM_ADMIN_ROLE))));

        assertThat(guard.isPlatformAdmin()).isTrue();
        assertThatCode(() -> guard.assertCanAccess(ORG_A)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a malformed tenant claim is ignored rather than trusted")
    void malformedClaimIsIgnored() {
        authenticate(jwtWith(Map.of(TenantAccessGuard.ORG_CLAIM, "not-a-uuid")));

        assertThat(guard.callerOrganizations()).isEmpty();
        assertThatThrownBy(() -> guard.assertCanAccess(ORG_A))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    @DisplayName("a null organization id is a programming error, not a silent allow")
    void nullOrganizationIsRejected() {
        authenticate(jwtWith(Map.of(TenantAccessGuard.ORG_CLAIM, ORG_A.toString())));

        assertThatThrownBy(() -> guard.assertCanAccess(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Jwt jwtWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("test-subject");
        claims.forEach(builder::claim);
        return builder.build();
    }
}

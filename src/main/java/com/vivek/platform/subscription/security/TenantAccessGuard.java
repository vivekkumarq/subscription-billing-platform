package com.vivek.platform.subscription.security;

import com.vivek.platform.subscription.exception.TenantAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces multi-tenant isolation.
 *
 * <p>Every org-scoped endpoint used to trust the organization id in the path or body, so any
 * caller holding any valid token could read (and bill against) any other tenant's data. This
 * guard requires the caller's token to actually claim membership of the organization it is
 * addressing, unless the caller holds the platform-admin role.</p>
 *
 * <p>Tenant membership is read from the {@code org_id} claim (single tenant) or the
 * {@code org_ids} claim (list). A token carrying neither claim and no admin role can reach no
 * organization at all - deny by default.</p>
 */
@Component
public class TenantAccessGuard {

    public static final String ORG_CLAIM = "org_id";
    public static final String ORGS_CLAIM = "org_ids";
    public static final String PLATFORM_ADMIN_ROLE = "ROLE_PLATFORM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(TenantAccessGuard.class);

    /**
     * @throws TenantAccessDeniedException if the current principal may not touch this organization
     */
    public void assertCanAccess(UUID organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }
        if (isPlatformAdmin()) {
            return;
        }
        Set<UUID> permitted = callerOrganizations();
        if (!permitted.contains(organizationId)) {
            log.warn("Denied cross-tenant access: principal={} requested org={} permitted={}",
                    principalName(), organizationId, permitted);
            throw new TenantAccessDeniedException(organizationId);
        }
    }

    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        return authorities != null && authorities.stream()
                .anyMatch(a -> PLATFORM_ADMIN_ROLE.equals(a.getAuthority()));
    }

    /** Organization ids the current token claims membership of. Never null. */
    public Set<UUID> callerOrganizations() {
        Set<UUID> result = new LinkedHashSet<>();
        Jwt jwt = currentJwt();
        if (jwt == null) {
            return result;
        }
        addIfUuid(result, jwt.getClaimAsString(ORG_CLAIM));
        Object multi = jwt.getClaim(ORGS_CLAIM);
        if (multi instanceof Collection<?> values) {
            values.forEach(v -> addIfUuid(result, v == null ? null : v.toString()));
        } else if (multi instanceof String single) {
            addIfUuid(result, single);
        }
        return result;
    }

    /**
     * The single organization the caller acts on behalf of, when the token names exactly one.
     * Used to default the tenant on write endpoints.
     */
    public UUID soleCallerOrganization() {
        List<UUID> orgs = List.copyOf(callerOrganizations());
        return orgs.size() == 1 ? orgs.get(0) : null;
    }

    private Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private String principalName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }

    private static void addIfUuid(Set<UUID> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            target.add(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring non-UUID tenant claim value: {}", raw);
        }
    }
}

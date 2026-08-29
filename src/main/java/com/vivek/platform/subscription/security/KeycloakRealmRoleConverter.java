package com.vivek.platform.subscription.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps Keycloak's {@code realm_access.roles} claim onto Spring Security authorities, since the
 * default converter only reads scopes.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaim(REALM_ACCESS);
        if (!(realmAccess instanceof Map)) {
            return Collections.emptyList();
        }
        Object roles = ((Map<String, Object>) realmAccess).get(ROLES);
        if (!(roles instanceof Collection<?> roleValues)) {
            return Collections.emptyList();
        }
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Object role : roleValues) {
            if (role != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()));
            }
        }
        return authorities;
    }
}

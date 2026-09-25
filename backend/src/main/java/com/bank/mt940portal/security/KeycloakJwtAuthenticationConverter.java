package com.bank.mt940portal.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps a Keycloak access token to Spring Security authorities.
 * <p>
 * Collects realm roles ({@code realm_access.roles}) plus client roles for every
 * entry under {@code resource_access}, so either style works depending on how
 * the Keycloak client is configured.
 */
public class KeycloakJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter =
            new JwtGrantedAuthoritiesConverter();

    private final String rolePrefix;

    public KeycloakJwtAuthenticationConverter(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));
        authorities.addAll(extractRoles(jwt));
        String principalClaim = jwt.getClaimAsString("preferred_username");
        if (!StringUtils.hasText(principalClaim)) {
            principalClaim = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, principalClaim);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        List<GrantedAuthority> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> realmRoles) {
            for (Object role : realmRoles) {
                roles.add(toAuthority(role));
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            for (Object value : resourceAccess.values()) {
                if (value instanceof Map<?, ?> client) {
                    Object clientRoles = client.get("roles");
                    if (clientRoles instanceof Collection<?> collection) {
                        for (Object role : collection) {
                            roles.add(toAuthority(role));
                        }
                    }
                }
            }
        }
        return roles;
    }

    private SimpleGrantedAuthority toAuthority(Object role) {
        String name = String.valueOf(role);
        return new SimpleGrantedAuthority(name.startsWith(rolePrefix) ? name : rolePrefix + name);
    }
}

package com.bank.mt940portal.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Access to the authenticated operator for audit and maker/checker checks.
 */
@Component
public class CurrentUser {

    private static final String SYSTEM = "system";

    private final SecurityRoles securityRoles;

    public CurrentUser(SecurityRoles securityRoles) {
        this.securityRoles = securityRoles;
    }

    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferred = jwt.getClaimAsString("preferred_username");
            return preferred != null ? preferred : jwt.getSubject();
        }
        return authentication.getName();
    }

    public String fullName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return SYSTEM;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String name = jwt.getClaimAsString("name");
            if (name != null) {
                return name;
            }
            String given = nullToEmpty(jwt.getClaimAsString("given_name"));
            String family = nullToEmpty(jwt.getClaimAsString("family_name"));
            String combined = (given + " " + family).trim();
            return combined.isEmpty() ? username() : combined;
        }
        return authentication.getName();
    }

    public List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    public String authoritiesCsv() {
        return String.join(",", authorities());
    }

    public boolean isMaker() {
        return hasAny(securityRoles.makerAuthorities());
    }

    public boolean isChecker() {
        return hasAny(securityRoles.checkerAuthorities());
    }

    public boolean isAdmin() {
        return authorities().contains(securityRoles.getAdminAuthority());
    }

    public boolean hasAny(String... authorities) {
        List<String> owned = authorities();
        for (String candidate : authorities) {
            if (owned.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public String sessionId() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(HttpServletRequest::getRequestedSessionId)
                .orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

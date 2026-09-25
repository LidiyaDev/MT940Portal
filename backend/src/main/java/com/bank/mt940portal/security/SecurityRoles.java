package com.bank.mt940portal.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the Keycloak realm role names that back the maker / checker model
 * into Spring Security authority strings.
 * <p>
 * Used from {@code @PreAuthorize} expressions, e.g.
 * {@code @PreAuthorize("hasAuthority(@securityRoles.makerAuthority)")}.
 */
@Component("securityRoles")
@Getter
public class SecurityRoles {

    private final String prefix;

    private final String makerAuthority;
    private final String checkerAuthority;
    private final String adminAuthority;
    private final String viewerAuthority;

    public SecurityRoles(
            @Value("${mt940.security.role-prefix}") String prefix,
            @Value("${mt940.security.maker-role}") String makerRole,
            @Value("${mt940.security.checker-role}") String checkerRole,
            @Value("${mt940.security.admin-role}") String adminRole,
            @Value("${mt940.security.viewer-role}") String viewerRole) {
        this.prefix = prefix;
        this.makerAuthority = prefix + makerRole;
        this.checkerAuthority = prefix + checkerRole;
        this.adminAuthority = prefix + adminRole;
        this.viewerAuthority = prefix + viewerRole;
    }

    /** Authorities allowed to read data. */
    public String[] readAuthorities() {
        return new String[]{viewerAuthority, makerAuthority, checkerAuthority, adminAuthority};
    }

    /** Authorities allowed to raise maker-checker requests. */
    public String[] makerAuthorities() {
        return new String[]{makerAuthority, adminAuthority};
    }

    /** Authorities allowed to approve or reject. */
    public String[] checkerAuthorities() {
        return new String[]{checkerAuthority, adminAuthority};
    }
}

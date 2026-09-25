package com.bank.mt940portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Development only: authenticates every request as a fixed operator so the
 * portal can be exercised without a running Keycloak.
 * <p>
 * Enabled with {@code mt940.security.dev-bypass.enabled=true}. The production
 * profile hard disables it.
 */
public class DevBypassAuthenticationFilter extends OncePerRequestFilter {

    private final String username;
    private final List<String> roles;
    private final String authorityPrefix;

    public DevBypassAuthenticationFilter(String username,
                                         List<String> roles,
                                         String authorityPrefix) {
        this.username = username;
        this.roles = roles;
        this.authorityPrefix = authorityPrefix;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> role.startsWith(authorityPrefix) ? role : authorityPrefix + role)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, "N/A", authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}

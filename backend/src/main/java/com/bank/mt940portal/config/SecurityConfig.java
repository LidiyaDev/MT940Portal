package com.bank.mt940portal.config;

import com.bank.mt940portal.security.DevBypassAuthenticationFilter;
import com.bank.mt940portal.security.KeycloakJwtAuthenticationConverter;
import com.bank.mt940portal.security.SecurityRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * The backend is a stateless OAuth2 resource server. Every request must carry a
 * Keycloak issued bearer token, except for health checks and the OpenAPI docs.
 * <p>
 * Coarse grained rules live here; per-operation rules use
 * {@code @PreAuthorize} on the service layer.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityRoles securityRoles,
                                           @Value("${mt940.security.dev-bypass.enabled:false}")
                                           boolean devBypass,
                                           @Value("${mt940.security.dev-bypass.username:dev}")
                                           String devUsername,
                                           @Value("${mt940.security.dev-bypass.roles:}")
                                           String[] devRoles) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsOrigins)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated());

        if (devBypass) {
            List<String> roles = Arrays.stream(devRoles)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            http.addFilterBefore(
                    new DevBypassAuthenticationFilter(devUsername, roles, securityRoles.getPrefix()),
                    org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(
                            new KeycloakJwtAuthenticationConverter(securityRoles.getPrefix()))));
        }

        return http.build();
    }

    private final String[] corsOrigins;

    public SecurityConfig(@Value("${mt940.cors.allowed-origins:}") String corsOrigins) {
        this.corsOrigins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return corsConfigurationSource(corsOrigins);
    }

    private CorsConfigurationSource corsConfigurationSource(String[] origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (origins.length == 0) {
            configuration.addAllowedOriginPattern("*");
        } else {
            configuration.setAllowedOrigins(Arrays.asList(origins));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Built from the JWKS endpoint rather than the OIDC discovery document so
     * that the application still starts when Keycloak is temporarily down: the
     * keys are fetched lazily on the first token validation and refreshed by
     * Nimbus afterwards.
     */
    @Bean
    @ConditionalOnProperty(name = "mt940.security.dev-bypass.enabled", havingValue = "false", matchIfMissing = true)
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${mt940.keycloak.jwk-set-uri:}") String jwkSetUri) {

        String jwks = StringUtils.hasText(jwkSetUri)
                ? jwkSetUri
                : issuerUri.replaceAll("/$", "") + "/protocol/openid-connect/certs";

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }
}

package com.bank.mt940portal.controller;

import com.bank.mt940portal.security.CurrentUser;
import com.bank.mt940portal.security.SecurityRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lets the UI adapt to the roles carried by the current Keycloak token. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentUser currentUser;
    private final SecurityRoles securityRoles;

    @GetMapping
    public ResponseEntity<Map<String, Object>> me() {
        return ResponseEntity.ok(Map.of(
                "username", currentUser.username(),
                "fullName", currentUser.fullName(),
                "authorities", currentUser.authorities(),
                "isMaker", currentUser.isMaker(),
                "isChecker", currentUser.isChecker(),
                "isAdmin", currentUser.isAdmin(),
                "roles", Map.of(
                        "maker", securityRoles.getMakerAuthority(),
                        "checker", securityRoles.getCheckerAuthority(),
                        "admin", securityRoles.getAdminAuthority(),
                        "viewer", securityRoles.getViewerAuthority())));
    }
}

package com.bank.mt940portal.controller;

import com.bank.mt940portal.dto.DashboardDto;
import com.bank.mt940portal.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardDto> dashboard() {
        return ResponseEntity.ok(dashboardService.build());
    }
}

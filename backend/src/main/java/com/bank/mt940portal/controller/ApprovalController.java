package com.bank.mt940portal.controller;

import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.dto.ApprovalDecisionRequest;
import com.bank.mt940portal.dto.ApprovalRequestDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The maker-checker queue. */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    public ResponseEntity<PageResponse<ApprovalRequestDto>> list(
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(approvalService.list(status, pageable));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalRequestDto>> pending() {
        return ResponseEntity.ok(approvalService.listPending());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        Map<String, Object> byEntity = approvalService.pendingSummary().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().name(), Map.Entry::getValue));
        return ResponseEntity.ok(Map.of(
                "pending", approvalService.countPending(),
                "byEntityType", byEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalRequestDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.get(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false)
                                                       ApprovalDecisionRequest decision) {
        var request = approvalService.approve(id, decision == null ? null : decision.getNote());
        return ResponseEntity.ok(Map.of("approvalId", request.getId(),
                "status", request.getStatus(),
                "message", "Request approved and applied"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id,
                                                      @Valid @RequestBody(required = false)
                                                      ApprovalDecisionRequest decision) {
        var request = approvalService.reject(id, decision == null ? null : decision.getNote());
        return ResponseEntity.ok(Map.of("approvalId", request.getId(),
                "status", request.getStatus(),
                "message", "Request rejected"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id,
                                                      @Valid @RequestBody(required = false)
                                                      ApprovalDecisionRequest decision) {
        var request = approvalService.cancel(id, decision == null ? null : decision.getNote());
        return ResponseEntity.ok(Map.of("approvalId", request.getId(),
                "status", request.getStatus(),
                "message", "Request cancelled"));
    }
}

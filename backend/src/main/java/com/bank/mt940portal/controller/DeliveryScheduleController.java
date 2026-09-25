package com.bank.mt940portal.controller;

import com.bank.mt940portal.dto.DeliveryScheduleDto;
import com.bank.mt940portal.service.DeliveryScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeliveryScheduleController {

    private final DeliveryScheduleService scheduleService;

    @GetMapping("/clients/{clientId}/schedules")
    public ResponseEntity<List<DeliveryScheduleDto>> listByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(scheduleService.listByClient(clientId));
    }

    @GetMapping("/accounts/{accountId}/schedules")
    public ResponseEntity<List<DeliveryScheduleDto>> listByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(scheduleService.listByAccount(accountId));
    }

    @GetMapping("/schedules/{id}")
    public ResponseEntity<DeliveryScheduleDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.get(id));
    }

    @PostMapping("/clients/{clientId}/schedules")
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long clientId,
                                                      @Valid @RequestBody DeliveryScheduleDto dto,
                                                      @RequestParam(required = false) String reason) {
        dto.setClientId(clientId);
        var approval = scheduleService.submitCreate(dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Delivery schedule submitted for approval"));
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody DeliveryScheduleDto dto,
                                                      @RequestParam(required = false) String reason) {
        var approval = scheduleService.submitUpdate(id, dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Delivery schedule update submitted for approval"));
    }

    @PatchMapping("/schedules/{id}/enabled")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id,
                                                      @RequestParam boolean enabled,
                                                      @RequestParam(required = false) String reason) {
        var approval = scheduleService.submitToggle(id, enabled, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Schedule " + (enabled ? "activation" : "deactivation")
                                + " submitted for approval"));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason) {
        var approval = scheduleService.submitDelete(id, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Schedule deletion submitted for approval"));
    }
}

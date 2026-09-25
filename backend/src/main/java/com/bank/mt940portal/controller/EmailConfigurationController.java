package com.bank.mt940portal.controller;

import com.bank.mt940portal.dto.EmailConfigurationDto;
import com.bank.mt940portal.service.EmailConfigurationService;
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
public class EmailConfigurationController {

    private final EmailConfigurationService emailConfigurationService;

    @GetMapping("/clients/{clientId}/email-config")
    public ResponseEntity<List<EmailConfigurationDto>> list(@PathVariable Long clientId) {
        return ResponseEntity.ok(emailConfigurationService.listByClient(clientId));
    }

    @GetMapping("/email-config/{id}")
    public ResponseEntity<EmailConfigurationDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(emailConfigurationService.get(id));
    }

    @PostMapping("/clients/{clientId}/email-config")
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long clientId,
                                                      @Valid @RequestBody EmailConfigurationDto dto,
                                                      @RequestParam(required = false) String reason) {
        dto.setClientId(clientId);
        var approval = emailConfigurationService.submitCreate(dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Email configuration submitted for approval"));
    }

    @PutMapping("/email-config/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody EmailConfigurationDto dto,
                                                      @RequestParam(required = false) String reason) {
        var approval = emailConfigurationService.submitUpdate(id, dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Email configuration update submitted for approval"));
    }

    @DeleteMapping("/email-config/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason) {
        var approval = emailConfigurationService.submitDelete(id, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Email configuration deletion submitted for approval"));
    }
}

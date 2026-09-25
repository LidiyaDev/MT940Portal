package com.bank.mt940portal.controller;

import com.bank.mt940portal.domain.enums.ClientStatus;
import com.bank.mt940portal.dto.ClientDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.service.ApprovalService;
import com.bank.mt940portal.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ApprovalService approvalService;

    @GetMapping
    public ResponseEntity<PageResponse<ClientDto>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(clientService.list(pageable, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.get(id));
    }

    /** Raises a maker-checker request; nothing is written until it is approved. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ClientDto dto,
                                                      @RequestParam(required = false) String reason) {
        var request = clientService.submitCreate(dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", request.getId(),
                        "status", request.getStatus(),
                        "message", "Client creation submitted for approval"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody ClientDto dto,
                                                      @RequestParam(required = false) String reason) {
        var request = clientService.submitUpdate(id, dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", request.getId(),
                        "status", request.getStatus(),
                        "message", "Client update submitted for approval"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> changeStatus(@PathVariable Long id,
                                                            @RequestParam ClientStatus status,
                                                            @RequestParam(required = false) String reason) {
        var request = clientService.submitStatusChange(id, status, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", request.getId(),
                        "status", request.getStatus(),
                        "message", "Client status change submitted for approval"));
    }
}

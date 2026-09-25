package com.bank.mt940portal.controller;

import com.bank.mt940portal.domain.enums.AccountStatus;
import com.bank.mt940portal.dto.AccountDto;
import com.bank.mt940portal.dto.BulkAccountRequest;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/clients/{clientId}/accounts")
    public ResponseEntity<PageResponse<AccountDto>> list(
            @PathVariable Long clientId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(accountService.listByClient(clientId, pageable));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.get(id));
    }

    /** Adds a single account - routed through the same approval flow as bulk. */
    @PostMapping("/clients/{clientId}/accounts")
    public ResponseEntity<Map<String, Object>> createSingle(
            @PathVariable Long clientId,
            @Valid @RequestBody AccountDto dto,
            @RequestParam(required = false) String reason) {
        dto.setClientId(clientId);
        BulkAccountRequest request = new BulkAccountRequest();
        request.setClientId(clientId);
        request.setAccounts(List.of(dto));
        request.setReason(reason);
        return submit(request);
    }

    /** Adds several accounts in one approval request. */
    @PostMapping("/clients/{clientId}/accounts/bulk")
    public ResponseEntity<Map<String, Object>> createBulk(
            @PathVariable Long clientId,
            @Valid @RequestBody BulkAccountRequest request,
            @RequestParam(required = false) String reason) {
        request.setClientId(clientId);
        if (reason != null) {
            request.setReason(reason);
        }
        return submit(request);
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody AccountDto dto,
                                                      @RequestParam(required = false) String reason) {
        var approval = accountService.submitUpdate(id, dto, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Account update submitted for approval"));
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<Map<String, Object>> changeStatus(@PathVariable Long id,
                                                            @RequestParam AccountStatus status,
                                                            @RequestParam(required = false) String reason) {
        var approval = accountService.submitStatusChange(id, status, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Account status change submitted for approval"));
    }

    private ResponseEntity<Map<String, Object>> submit(BulkAccountRequest request) {
        var approval = accountService.submitCreate(request);
        int count = request.getAccounts() == null ? 0 : request.getAccounts().size();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", count + " account(s) submitted for approval"));
    }
}

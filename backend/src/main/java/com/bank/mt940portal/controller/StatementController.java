package com.bank.mt940portal.controller;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.Auditable;
import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.dto.GenerateStatementRequest;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.dto.SendStatementRequest;
import com.bank.mt940portal.dto.StatementDto;
import com.bank.mt940portal.service.ApprovalService;
import com.bank.mt940portal.service.StatementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;
    private final ApprovalService approvalService;

    /**
     * Renders the MT940 without storing it, so the operator can check the output
     * before committing to a generation.
     */
    @PostMapping("/preview")
    public ResponseEntity<StatementDto> preview(
            @RequestParam Long accountId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate periodFrom,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate periodTo) {
        return ResponseEntity.ok(statementService.preview(accountId, periodFrom, periodTo));
    }

    @PostMapping("/preview-body")
    public ResponseEntity<StatementDto> previewBody(@Valid @RequestBody GenerateStatementRequest request) {
        return ResponseEntity.ok(statementService.preview(
                request.getAccountId(), request.getPeriodFrom(), request.getPeriodTo()));
    }

    /** Generates and stores a statement. Emailing it is a separate, checked step. */
    @PostMapping("/generate")
    public ResponseEntity<StatementDto> generate(@Valid @RequestBody GenerateStatementRequest request) {
        Statement statement = statementService.generate(
                request.getAccountId(), request.getPeriodFrom(), request.getPeriodTo(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(statementService.toDto(statement));
    }

    /**
     * Manual delivery. Nothing is emailed here - the request enters the
     * maker-checker queue and is only sent once a checker approves it.
     */
    @PostMapping("/send-request")
    public ResponseEntity<Map<String, Object>> requestSend(
            @Valid @RequestBody SendStatementRequest request,
            @RequestParam(required = false) String reason) {
        var approval = approvalService.submitStatementSend(request, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("approvalId", approval.getId(),
                        "status", approval.getStatus(),
                        "message", "Statement delivery submitted for approval"));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StatementDto>> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long accountId,
            @PageableDefault(size = 25) Pageable pageable) {
        if (accountId != null) {
            List<StatementDto> all = statementService.listByAccount(accountId);
            return ResponseEntity.ok(PageResponse.<StatementDto>builder()
                    .content(all)
                    .page(0)
                    .size(all.size())
                    .totalElements(all.size())
                    .totalPages(1)
                    .last(true)
                    .build());
        }
        if (clientId != null) {
            return ResponseEntity.ok(statementService.listByClient(clientId, pageable));
        }
        return ResponseEntity.ok(statementService.listAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StatementDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(statementService.get(id, true));
    }

    @Auditable(action = AuditAction.STATEMENT_DOWNLOAD, entityType = "STATEMENT",
            description = "Downloaded an MT940 statement file")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Statement statement = statementService.findEntity(id);
        byte[] content = statementService.fileContent(statement);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + statement.getFileName() + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }
}

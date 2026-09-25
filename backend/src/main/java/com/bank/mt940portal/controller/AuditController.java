package com.bank.mt940portal.controller;

import com.bank.mt940portal.dto.AuditLogDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogDto>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "eventTime", direction = Sort.Direction.DESC) Pageable pageable) {

        AuditQueryService.AuditFilter filter = new AuditQueryService.AuditFilter(
                from, to, actor, action, entityType, entityId, outcome, correlationId, search);
        return ResponseEntity.ok(auditQueryService.search(filter, pageable));
    }

    /** Trail for one record, used by the detail drawer. */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLogDto>> history(@PathVariable String entityType,
                                                     @PathVariable String entityId) {
        return ResponseEntity.ok(auditQueryService.history(entityType, entityId));
    }
}

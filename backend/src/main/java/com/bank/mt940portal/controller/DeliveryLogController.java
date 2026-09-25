package com.bank.mt940portal.controller;

import com.bank.mt940portal.domain.DeliveryLog;
import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.dto.DeliveryLogDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.repository.DeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryLogController {

    private final DeliveryLogRepository deliveryLogRepository;

    @GetMapping
    public ResponseEntity<PageResponse<DeliveryLogDto>> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) DeliveryStatus status,
            @PageableDefault(size = 50, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DeliveryLog> page = clientId == null
                ? deliveryLogRepository.findAllByOrderBySentAtDesc(pageable)
                : deliveryLogRepository.findAllByClientIdOrderBySentAtDesc(clientId, pageable);

        List<DeliveryLogDto> content = page.getContent().stream()
                .map(this::toDto)
                .filter(dto -> status == null || dto.getStatus() == status)
                .toList();

        return ResponseEntity.ok(PageResponse.<DeliveryLogDto>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build());
    }

    private DeliveryLogDto toDto(DeliveryLog log) {
        return DeliveryLogDto.builder()
                .id(log.getId())
                .statementId(log.getStatement() == null ? null : log.getStatement().getId())
                .statementReference(log.getStatement() == null
                        ? null : log.getStatement().getStatementReference())
                .clientId(log.getClient() == null ? null : log.getClient().getId())
                .clientCode(log.getClient() == null ? null : log.getClient().getClientCode())
                .accountId(log.getAccount() == null ? null : log.getAccount().getId())
                .accountNumber(log.getAccount() == null ? null : log.getAccount().getAccountNumber())
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .status(log.getStatus())
                .attempt(log.getAttempt())
                .provider(log.getProvider())
                .providerResponse(log.getProviderResponse())
                .errorMessage(log.getErrorMessage())
                .correlationId(log.getCorrelationId())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .build();
    }
}

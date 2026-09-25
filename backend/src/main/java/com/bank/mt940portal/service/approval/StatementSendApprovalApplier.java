package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.SendStatementRequest;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.repository.StatementRepository;
import com.bank.mt940portal.service.StatementDeliveryService;
import com.bank.mt940portal.service.StatementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Approval path for a manual "generate and email now" request: nothing is sent
 * until a checker signs it off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatementSendApprovalApplier implements ApprovalApplier {

    private final StatementService statementService;
    private final StatementDeliveryService deliveryService;
    private final StatementRepository statementRepository;
    private final ObjectMapper objectMapper;

    @Override
    public EntityType supports() {
        return EntityType.STATEMENT;
    }

    @Override
    public Object apply(ApprovalRequest request) {
        SendStatementRequest payload = read(request);
        Statement statement;

        if (payload.getStatementId() != null) {
            statement = statementService.findEntity(payload.getStatementId());
        } else if (payload.getAccountId() != null) {
            statement = statementService.generate(payload.getAccountId(),
                    payload.getPeriodFrom(), payload.getPeriodTo(), null);
            statementRepository.save(statement);
        } else {
            throw new BusinessException("Approved send request has neither a statement nor an account");
        }

        return deliveryService.send(statement, payload.getRecipients(),
                request.getReviewedBy() == null ? request.getRequestedBy() : request.getReviewedBy(), null);
    }

    private SendStatementRequest read(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), SendStatementRequest.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored send request could not be read: " + ex.getMessage());
        }
    }
}

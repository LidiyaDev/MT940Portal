package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.AccountDto;
import com.bank.mt940portal.dto.BulkAccountRequest;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountApprovalApplier implements ApprovalApplier {

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @Override
    public EntityType supports() {
        return EntityType.ACCOUNT;
    }

    @Override
    public Object apply(ApprovalRequest request) {
        return switch (request.getOperation()) {
            case CREATE -> accountService.applyCreate(readBulk(request));
            case UPDATE -> accountService.applyUpdate(request.getEntityId(), readSingle(request));
            default -> throw new BusinessException(
                    "Unsupported account operation: " + request.getOperation());
        };
    }

    private BulkAccountRequest readBulk(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), BulkAccountRequest.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored account payload could not be read: " + ex.getMessage());
        }
    }

    private AccountDto readSingle(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), AccountDto.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored account payload could not be read: " + ex.getMessage());
        }
    }
}

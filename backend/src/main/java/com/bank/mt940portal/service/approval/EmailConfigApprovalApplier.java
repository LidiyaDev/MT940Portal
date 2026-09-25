package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.EmailConfigurationDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.service.EmailConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConfigApprovalApplier implements ApprovalApplier {

    private final EmailConfigurationService emailConfigurationService;
    private final ObjectMapper objectMapper;

    @Override
    public EntityType supports() {
        return EntityType.EMAIL_CONFIG;
    }

    @Override
    public Object apply(ApprovalRequest request) {
        EmailConfigurationDto payload = read(request);
        return switch (request.getOperation()) {
            case CREATE -> emailConfigurationService.applyCreate(payload);
            case UPDATE -> emailConfigurationService.applyUpdate(request.getEntityId(), payload);
            case DELETE -> {
                emailConfigurationService.applyDelete(request.getEntityId());
                yield null;
            }
            default -> throw new BusinessException(
                    "Unsupported email configuration operation: " + request.getOperation());
        };
    }

    private EmailConfigurationDto read(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), EmailConfigurationDto.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored email configuration payload could not be read: " + ex.getMessage());
        }
    }
}

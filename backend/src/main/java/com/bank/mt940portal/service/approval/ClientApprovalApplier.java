package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.ClientDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.service.ClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientApprovalApplier implements ApprovalApplier {

    private final ClientService clientService;
    private final ObjectMapper objectMapper;

    @Override
    public EntityType supports() {
        return EntityType.CLIENT;
    }

    @Override
    public Object apply(ApprovalRequest request) {
        ClientDto payload = read(request);
        return switch (request.getOperation()) {
            case CREATE -> clientService.applyCreate(payload);
            case UPDATE -> clientService.applyUpdate(request.getEntityId(), payload);
            default -> throw new BusinessException(
                    "Unsupported client operation: " + request.getOperation());
        };
    }

    private ClientDto read(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), ClientDto.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored client payload could not be read: " + ex.getMessage());
        }
    }
}

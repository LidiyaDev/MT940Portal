package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.DeliveryScheduleDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.service.DeliveryScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleApprovalApplier implements ApprovalApplier {

    private final DeliveryScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    @Override
    public EntityType supports() {
        return EntityType.DELIVERY_SCHEDULE;
    }

    @Override
    public Object apply(ApprovalRequest request) {
        DeliveryScheduleDto payload = read(request);
        return switch (request.getOperation()) {
            case CREATE -> scheduleService.applyCreate(payload);
            case UPDATE -> scheduleService.applyUpdate(request.getEntityId(), payload);
            case DELETE -> {
                scheduleService.applyDelete(request.getEntityId());
                yield null;
            }
            default -> throw new BusinessException(
                    "Unsupported schedule operation: " + request.getOperation());
        };
    }

    private DeliveryScheduleDto read(ApprovalRequest request) {
        try {
            return objectMapper.readValue(request.getPayloadJson(), DeliveryScheduleDto.class);
        } catch (Exception ex) {
            throw new BusinessException("Stored schedule payload could not be read: " + ex.getMessage());
        }
    }
}

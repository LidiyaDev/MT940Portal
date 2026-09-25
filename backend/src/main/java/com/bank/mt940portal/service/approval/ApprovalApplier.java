package com.bank.mt940portal.service.approval;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.EntityType;

/**
 * Applies an approved maker-checker request to the live tables.
 * <p>
 * One implementation per entity type; {@code ApprovalService} picks the right
 * one from the injected list. Appliers never depend on the approval service, so
 * there is no cycle back into the queue.
 */
public interface ApprovalApplier {

    EntityType supports();

    /**
     * Applies the change. Throwing leaves the request in its current state and
     * the failure is surfaced to the checker.
     */
    Object apply(ApprovalRequest request);
}

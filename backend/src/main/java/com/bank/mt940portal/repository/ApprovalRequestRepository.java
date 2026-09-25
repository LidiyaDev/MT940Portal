package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findAllByStatusOrderByRequestedAtAsc(ApprovalStatus status);

    Page<ApprovalRequest> findAllByStatusOrderByRequestedAtDesc(ApprovalStatus status, Pageable pageable);

    Page<ApprovalRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<ApprovalRequest> findAllByRequestedByOrderByRequestedAtDesc(String requestedBy, Pageable pageable);

    /**
     * A pending request already exists for this entity - blocks duplicates and
     * stops a second edit being queued on top of an unapproved one.
     */
    Optional<ApprovalRequest> findFirstByEntityTypeAndEntityIdAndStatus(
            EntityType entityType, Long entityId, ApprovalStatus status);

    List<ApprovalRequest> findAllByEntityTypeAndEntityIdOrderByRequestedAtDesc(
            EntityType entityType, Long entityId);

    long countByStatus(ApprovalStatus status);
}

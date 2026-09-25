package com.bank.mt940portal.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop50ByEntityTypeAndEntityIdOrderByEventTimeDesc(String entityType, String entityId);

    Page<AuditLog> findAllByEventTimeBetweenOrderByEventTimeDesc(LocalDateTime from,
                                                                 LocalDateTime to,
                                                                 Pageable pageable);

    @Query("select count(a) from AuditLog a where a.eventTime >= :since and a.outcome = 'FAILURE'")
    long countFailuresSince(@Param("since") LocalDateTime since);
}

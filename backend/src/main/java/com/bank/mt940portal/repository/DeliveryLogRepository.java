package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.DeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {

    Page<DeliveryLog> findAllByOrderBySentAtDesc(Pageable pageable);

    Page<DeliveryLog> findAllByClientIdOrderBySentAtDesc(Long clientId, Pageable pageable);

    @Query("select count(d) from DeliveryLog d where d.status = 'SENT' and d.sentAt >= :since")
    long countSentSince(@Param("since") LocalDateTime since);

    @Query("select count(d) from DeliveryLog d where d.status = 'FAILED' and d.sentAt >= :since")
    long countFailedSince(@Param("since") LocalDateTime since);
}

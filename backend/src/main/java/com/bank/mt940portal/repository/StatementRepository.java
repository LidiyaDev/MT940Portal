package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.domain.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findAllByAccountIdOrderByGeneratedAtDesc(Long accountId);

    Page<Statement> findAllByClientIdOrderByGeneratedAtDesc(Long clientId, Pageable pageable);

    List<Statement> findAllByDeliveryStatusOrderByGeneratedAtAsc(DeliveryStatus status);

    @Query("select max(s.statementNumber) from Statement s where s.account.id = :accountId")
    Optional<Long> findMaxStatementNumber(@Param("accountId") Long accountId);

    @Query("select s from Statement s where s.account.id = :accountId "
            + "and s.periodFrom = :from and s.periodTo = :to")
    List<Statement> findByAccountAndPeriod(@Param("accountId") Long accountId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    Page<Statement> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    @Query("select count(s) from Statement s where s.generatedAt >= :since")
    long countGeneratedSince(@Param("since") java.time.LocalDateTime since);
}

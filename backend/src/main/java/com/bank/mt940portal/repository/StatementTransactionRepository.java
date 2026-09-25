package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.StatementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementTransactionRepository extends JpaRepository<StatementTransaction, Long> {

    List<StatementTransaction> findAllByStatementIdOrderByLineNoAsc(Long statementId);
}

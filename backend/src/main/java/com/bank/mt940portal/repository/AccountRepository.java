package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findAllByClientIdOrderByAccountNumberAsc(Long clientId);

    List<Account> findAllByClientIdAndStatusOrderByAccountNumberAsc(Long clientId, AccountStatus status);

    Page<Account> findAllByClientIdOrderByAccountNumberAsc(Long clientId, Pageable pageable);

    Set<String> findAccountNumbersByClientId(Long clientId);

    @Query("select count(a) from Account a where a.client.id = :clientId")
    long countByClient(@Param("clientId") Long clientId);

    long countByStatus(AccountStatus status);
}

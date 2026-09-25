package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.EmailConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailConfigurationRepository extends JpaRepository<EmailConfiguration, Long> {

    List<EmailConfiguration> findAllByClientIdOrderByIdAsc(Long clientId);

    Optional<EmailConfiguration> findFirstByClientIdAndEnabledTrueOrderByIsDefaultDescIdAsc(Long clientId);

    List<EmailConfiguration> findAllByClientIsNullOrderByIdAsc();
}

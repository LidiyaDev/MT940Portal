package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByClientCode(String clientCode);

    boolean existsByClientCodeIgnoreCase(String clientCode);

    List<Client> findAllByOrderByClientNameAsc();

    Page<Client> findAllByStatusOrderByClientNameAsc(ClientStatus status, Pageable pageable);

    @Query("select c from Client c where lower(c.clientName) like lower(concat('%', :q, '%')) "
            + "or lower(c.clientCode) like lower(concat('%', :q, '%'))")
    Page<Client> search(@Param("q") String q, Pageable pageable);

    long countByStatus(ClientStatus status);
}

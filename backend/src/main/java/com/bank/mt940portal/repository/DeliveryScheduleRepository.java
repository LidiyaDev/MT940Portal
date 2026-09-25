package com.bank.mt940portal.repository;

import com.bank.mt940portal.domain.DeliverySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeliveryScheduleRepository extends JpaRepository<DeliverySchedule, Long> {

    List<DeliverySchedule> findAllByClientIdOrderByIdAsc(Long clientId);

    List<DeliverySchedule> findAllByAccountIdOrderByIdAsc(Long accountId);

    List<DeliverySchedule> findAllByEnabledTrueOrderByNextRunAtAsc();

    @Query("select s from DeliverySchedule s where s.enabled = true "
            + "and (s.nextRunAt is null or s.nextRunAt <= :now) order by s.nextRunAt asc")
    List<DeliverySchedule> findDueSchedules(@Param("now") LocalDateTime now);
}

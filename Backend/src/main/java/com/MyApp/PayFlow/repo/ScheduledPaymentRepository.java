package com.MyApp.PayFlow.repo;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPaymentEntity , UUID> {

    @Query("""
            SELECT s FROM ScheduledPaymentEntity s
            WHERE s.status = 'ACTIVE'
            AND s.nextExecution <= :now
            """)
    List<ScheduledPaymentEntity> findPaymentsToExecute(Instant now);
}

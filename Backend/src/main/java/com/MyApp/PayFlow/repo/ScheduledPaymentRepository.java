package com.MyApp.PayFlow.repo;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Fetch scheduled payments for a sender
    Page<ScheduledPaymentEntity> findBySenderOrderByScheduledAtDesc(UserEntity sender, Pageable pageable);

    // Optional: filter by date range
    Page<ScheduledPaymentEntity> findBySenderAndScheduledAtBetweenOrderByScheduledAtDesc(
            UserEntity sender, Instant from, Instant to, Pageable pageable
    );
}

package com.MyApp.PayFlow.repo;

import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepo extends JpaRepository<TransactionEntity , UUID> {

    Optional<TransactionEntity> findByIdempotencyKeyAndUserAndType(
            String idempotencyKey,
            UserEntity user,
            TransactionType type
    );

    Page<TransactionEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    Page<TransactionEntity> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            UserEntity user,
            Instant from,
            Instant to,
            Pageable pageable
    );


}

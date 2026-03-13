package com.MyApp.PayFlow.service;

import com.MyApp.PayFlow.dto.ScheduledPaymentDto;
import com.MyApp.PayFlow.dto.TransactinoHistoryDto;
import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepo transactionRepo;
    private final UserRepository userRepo;
    private final ScheduledPaymentRepository scheduledPaymentRepo;


    public Page<TransactinoHistoryDto> getUserTransactionHistory(
            String email ,
            int page,
            int size,
            String dateFrom,
            String dateTo
    ) {

        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);

        Instant from = (dateFrom != null && !dateFrom.isEmpty()) ? Instant.parse(dateFrom) : null;
        Instant to = (dateTo != null && !dateTo.isEmpty()) ? Instant.parse(dateTo) : null;

        Page<TransactionEntity> transactions;
        if (from != null && to != null) {
            transactions = transactionRepo.findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
                    user, from, to, pageable
            );
        } else {
            transactions = transactionRepo.findByUserOrderByCreatedAtDesc(user, pageable);
        }

        //  Map to DTO to avoid infinite loops
        return transactions.map(tx -> new TransactinoHistoryDto(
                tx.getId(),
                tx.getAmount(),
                tx.getType().name(),
                tx.getCreatedAt()
        ));
    }

    public Page<ScheduledPaymentDto> getScheduledPayments(int page, int size, String dateFrom, String dateTo) {
        // Get authenticated user
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);

        Instant from = (dateFrom != null && !dateFrom.isEmpty()) ? Instant.parse(dateFrom) : null;
        Instant to = (dateTo != null && !dateTo.isEmpty()) ? Instant.parse(dateTo) : null;

        Page<ScheduledPaymentEntity> payments;

        if (from != null && to != null) {
            payments = scheduledPaymentRepo.findBySenderAndScheduledAtBetweenOrderByScheduledAtDesc(
                    user, from, to, pageable
            );
        } else {
            payments = scheduledPaymentRepo.findBySenderOrderByScheduledAtDesc(user, pageable);
        }

        //  Map to DTO
        return payments.map(p -> new ScheduledPaymentDto(
                p.getId(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getAmount(),
                p.getScheduledAt(),
                p.getLastExecuted(),
                p.getNextExecution(),
                p.getStatus().name(),
                p.getScheduleType().name()
        ));
    }

}

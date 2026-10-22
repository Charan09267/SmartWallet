
package com.MyApp.PayFlow.service.scheduledPayment;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.ScheduledPaymentStatus;
import com.MyApp.PayFlow.entity.enums.TransactionStatus;
import com.MyApp.PayFlow.entity.enums.TransactionType;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.WalletRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledPaymentProcessor {

    private static final int MAX_RETRIES = 3;

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final TransactionRepo transactionRepo;
    private final WalletRepo walletRepo;

    /**
     * Executes one scheduled payment atomically.
     *
     * If a database operation fails, the exception is allowed to escape
     * so Spring rolls back the complete payment transaction.
     */
    @Transactional
    public void processPayment(java.util.UUID paymentId) {

        ScheduledPaymentEntity payment =
                scheduledPaymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Scheduled payment not found: " + paymentId
                                )
                        );

        // Prevent reprocessing payments that have already finished.
        if (payment.getStatus() != ScheduledPaymentStatus.ACTIVE) {
            log.info(
                    "Skipping payment because it is not ACTIVE. paymentId={}, status={}",
                    paymentId,
                    payment.getStatus()
            );
            return;
        }

        WalletEntity senderWallet = payment.getSenderWallet();
        WalletEntity receiverWallet = payment.getReceiverWallet();

        // Insufficient funds are treated as a permanent failure here.
        // Change this policy if your application should retry later.
        if (senderWallet.getBalance().compareTo(payment.getAmount()) < 0) {
            payment.setStatus(ScheduledPaymentStatus.FAILED);
            scheduledPaymentRepository.save(payment);

            log.warn("Payment failed due to insufficient funds. paymentId={}", paymentId);
            return;
        }

        // Update both wallet balances within the same database transaction.
        senderWallet.setBalance(
                senderWallet.getBalance().subtract(payment.getAmount())
        );

        receiverWallet.setBalance(
                receiverWallet.getBalance().add(payment.getAmount())
        );

        walletRepo.save(senderWallet);
        walletRepo.save(receiverWallet);

        // Create the sender's debit transaction.
        TransactionEntity debitTx = createTransaction(
                payment,
                payment.getSender(),
                senderWallet,
                TransactionType.DEBIT
        );

        transactionRepo.save(debitTx);

        // Create the receiver's credit transaction.
        TransactionEntity creditTx = createTransaction(
                payment,
                payment.getReceiver(),
                receiverWallet,
                TransactionType.CREDIT
        );

        transactionRepo.save(creditTx);

        // Mark the payment completed only after all payment operations succeed.
        payment.setLastExecuted(Instant.now());
        payment.setStatus(ScheduledPaymentStatus.COMPLETED);

        scheduledPaymentRepository.save(payment);

        log.info("Scheduled payment completed. paymentId={}", paymentId);
    }

    /**
     * Creates a transaction record associated with a scheduled payment.
     */
    private TransactionEntity createTransaction(
            ScheduledPaymentEntity payment,
            com.MyApp.PayFlow.entity.UserEntity user,
            WalletEntity wallet,
            TransactionType type
    ) {
        TransactionEntity transaction = new TransactionEntity();

        transaction.setUser(user);
        transaction.setWallet(wallet);
        transaction.setAmount(payment.getAmount());
        transaction.setType(type);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setReferenceType("SCHEDULED_PAYMENT");
        transaction.setReferenceId(payment.getId());

        return transaction;
    }

    /**
     * Records a failed execution attempt in a separate transaction.
     * This method is called only after the payment transaction has failed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(java.util.UUID paymentId) {

        ScheduledPaymentEntity payment =
                scheduledPaymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Scheduled payment not found: " + paymentId
                                )
                        );

        // Do not change a payment that has already reached a terminal state.
        if (payment.getStatus() != ScheduledPaymentStatus.ACTIVE) {
            return;
        }

        int retryCount = payment.getRetryCount() == null
                ? 0
                : payment.getRetryCount();

        retryCount++;
        payment.setRetryCount(retryCount);

        if (retryCount >= MAX_RETRIES) {
            payment.setStatus(ScheduledPaymentStatus.FAILED);

            log.error(
                    "Payment marked FAILED after {} attempts. paymentId={}",
                    retryCount,
                    paymentId
            );
        } else {
            log.warn(
                    "Payment will be retried. paymentId={}, attempt={}/{}",
                    paymentId,
                    retryCount,
                    MAX_RETRIES
            );
        }

        scheduledPaymentRepository.save(payment);
    }
}


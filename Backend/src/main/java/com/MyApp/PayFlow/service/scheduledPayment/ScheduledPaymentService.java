
package com.MyApp.PayFlow.service.scheduledPayment;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.ScheduleType;
import com.MyApp.PayFlow.entity.enums.ScheduledPaymentStatus;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduledPaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(ScheduledPaymentService.class);

    private final UserRepository userRepository;
    private final WalletRepo walletRepo;
    private final ScheduledPaymentRepository scheduledPaymentRepository;

    /**
     * Creates a one-time scheduled payment between two users.
     *
     * This method records the payment schedule; it does not transfer
     * money immediately. The scheduled-payment execution process is
     * responsible for processing it when the scheduled time arrives.
     *
     * @param senderEmail email of the user scheduling the payment
     * @param receiverEmail email of the recipient
     * @param amount amount to transfer; must be positive
     * @param message optional payment description
     * @param scheduledAt future execution timestamp
     * @return the persisted scheduled payment
     */
    @Transactional
    public ScheduledPaymentEntity schedulePayment(
            String senderEmail,
            String receiverEmail,
            BigDecimal amount,
            String message,
            Instant scheduledAt) {

        // Validate the request before accessing the database.
        validateAmount(amount);
        validateScheduledAt(scheduledAt);
        validateEmails(senderEmail, receiverEmail);

        if (senderEmail.trim().equalsIgnoreCase(
                receiverEmail.trim())) {
            throw new IllegalArgumentException(
                    "Cannot schedule a payment to yourself");
        }

        UserEntity sender = userRepository.findByEmail(
                        senderEmail.trim())
                .orElseThrow(() ->
                        new EntityNotFoundException("Sender not found"));

        UserEntity receiver = userRepository.findByEmail(
                        receiverEmail.trim())
                .orElseThrow(() ->
                        new EntityNotFoundException("Receiver not found"));

        // Retrieve the wallets associated with both users.
        WalletEntity senderWallet = walletRepo.findByUserId(sender.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Sender wallet not found"));

        WalletEntity receiverWallet =
                walletRepo.findByUserId(receiver.getId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Receiver wallet not found"));

        // Direct transfers require compatible wallet currencies.
        if (!senderWallet.getCurrency()
                .equals(receiverWallet.getCurrency())) {
            throw new IllegalArgumentException(
                    "Wallet currencies do not match");
        }

        ScheduledPaymentEntity payment = ScheduledPaymentEntity.builder()
                .sender(sender)
                .senderWallet(senderWallet)
                .receiver(receiver)
                .receiverWallet(receiverWallet)
                .amount(amount)
                .message(message)
                .status(ScheduledPaymentStatus.ACTIVE)
                .scheduleType(ScheduleType.ONE_TIME)
                .scheduledAt(scheduledAt)
                .nextExecution(scheduledAt)
                .build();


        ScheduledPaymentEntity savedPayment =
                scheduledPaymentRepository.save(payment);

        log.info(
                "Scheduled payment created. Payment ID: {}, scheduled at: {}",
                savedPayment.getId(),
                scheduledAt
        );

        return savedPayment;
    }

    /**
     * Cancels an active scheduled payment owned by the requesting user.
     *
     * Completed, failed, and already-cancelled payments cannot be
     * cancelled again.
     *
     * @param paymentId identifier of the scheduled payment
     * @param userEmail email of the requesting user
     */
    @Transactional
    public void cancelScheduledPayment(
            UUID paymentId,
            String userEmail) {

        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "Payment ID is required");
        }

        if (userEmail == null || userEmail.isBlank()) {
            throw new AccessDeniedException(
                    "Authenticated user is required");
        }

        log.info(
                "Cancellation requested for payment ID: {}",
                paymentId
        );

        ScheduledPaymentEntity payment =
                scheduledPaymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Scheduled payment not found"));

        // Ensure that only the payment's sender can cancel it.
        if (payment.getSender().getEmail() == null
                || !payment.getSender().getEmail()
                .equalsIgnoreCase(userEmail.trim())) {
            throw new AccessDeniedException(
                    "You are not allowed to cancel this payment");
        }

        // Only ACTIVE payments are cancellable in this workflow.
        if (payment.getStatus() != ScheduledPaymentStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active scheduled payments can be cancelled");
        }

        // CANCELLED is distinct from FAILED: the user requested
        // cancellation rather than the execution process failing.
        payment.setStatus(ScheduledPaymentStatus.CANCELLED);

        scheduledPaymentRepository.save(payment);

        log.info(
                "Scheduled payment cancelled. Payment ID: {}",
                paymentId
        );
    }

    /**
     * Validates that the payment amount is positive.
     */
    private void validateAmount(BigDecimal amount) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero");
        }
    }

    /**
     * Validates that the execution time is in the future.
     */
    private void validateScheduledAt(Instant scheduledAt) {

        if (scheduledAt == null) {
            throw new IllegalArgumentException(
                    "Scheduled time is required");
        }

        if (!scheduledAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Scheduled time must be in the future");
        }
    }

    /**
     * Ensures both sender and receiver email addresses are provided.
     */
    private void validateEmails(
            String senderEmail,
            String receiverEmail) {

        if (senderEmail == null || senderEmail.isBlank()
                || receiverEmail == null
                || receiverEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender and receiver emails are required");
        }
    }
}

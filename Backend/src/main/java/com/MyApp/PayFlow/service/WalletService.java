package com.MyApp.PayFlow.service;


import com.MyApp.PayFlow.AsyncProcesses.PaymentProcessor;
import com.MyApp.PayFlow.dto.WalletBalanceResponse;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.TransactionStatus;
import com.MyApp.PayFlow.entity.enums.TransactionType;
import com.MyApp.PayFlow.events.PaymentEvent;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepo walletRepo;
    private final UserRepository userRepository;
    private final TransactionRepo transactionRepo;
    private final ApplicationEventPublisher publisher;

    private static final Logger log =
            LoggerFactory.getLogger(WalletService.class);

    /**
     * Retrieves the current balance and currency of a user's wallet.
     *
     * @param userId identifier of the wallet owner
     * @return wallet balance details
     * @throws EntityNotFoundException if the wallet does not exist
     */
    @Transactional(readOnly = true)
    public WalletBalanceResponse getWalletBalance(UUID userId) {

        WalletEntity wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Wallet not found"));

        return new WalletBalanceResponse(
                userId,
                wallet.getBalance(),
                wallet.getCurrency()
        );
    }

    /**
     * Initiates a wallet top-up.
     *
     * The transaction is initially stored as PROCESSING. The payment
     * processing flow is triggered through an application event.
     *
     * @param email email of the wallet owner
     * @param amount amount to add; must be positive
     * @param idempotencyKey key used to identify duplicate requests
     * @return the newly created or previously existing credit transaction
     */
    @Transactional
    public TransactionEntity addMoney(
            String email,
            BigDecimal amount,
            String idempotencyKey) {

        validateAmount(amount);
        validateIdempotencyKey(idempotencyKey);

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found"));

        // Return the original transaction when this request was
        // already processed using the same idempotency key.
        Optional<TransactionEntity> existing =
                transactionRepo.findByIdempotencyKeyAndUserAndType(
                        idempotencyKey,
                        user,
                        TransactionType.CREDIT
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        WalletEntity wallet = walletRepo.findByUserId(user.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Wallet not found"));

        TransactionEntity transaction = new TransactionEntity();

        transaction.setUser(user);
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setType(TransactionType.CREDIT);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setReferenceType("ADD_MONEY");
        transaction.setIdempotencyKey(idempotencyKey);

        // Persist the transaction before publishing its event.
        transactionRepo.save(transaction);

        // The event listener should process this event AFTER_COMMIT.
        // This prevents it from processing an uncommitted transaction.
        publisher.publishEvent(new PaymentEvent(transaction.getId()));

        return transaction;
    }

    /**
     * Transfers money atomically between two wallets.
     *
     * The sender's debit, receiver's credit, and both wallet balance
     * updates participate in the same database transaction.
     *
     * @param senderEmail email of the sender
     * @param receiverEmail email of the receiver
     * @param amount amount to transfer; must be positive
     * @param idempotencyKey key used to identify duplicate requests
     * @return the sender's completed debit transaction
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public TransactionEntity transferMoney(
            String senderEmail,
            String receiverEmail,
            BigDecimal amount,
            String idempotencyKey) {

        validateAmount(amount);
        validateIdempotencyKey(idempotencyKey);

        if (senderEmail == null || receiverEmail == null) {
            throw new IllegalArgumentException(
                    "Sender and receiver emails are required");
        }

        if (senderEmail.equalsIgnoreCase(receiverEmail)) {
            throw new IllegalArgumentException(
                    "Cannot transfer money to yourself");
        }

        UserEntity sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() ->
                        new EntityNotFoundException("Sender not found"));

        UserEntity receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() ->
                        new EntityNotFoundException("Receiver not found"));

        // Check whether this sender has already initiated the transfer.
        Optional<TransactionEntity> existing =
                transactionRepo.findByIdempotencyKeyAndUserAndType(
                        idempotencyKey,
                        sender,
                        TransactionType.DEBIT
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        WalletEntity senderWallet = walletRepo.findByUserId(sender.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Sender wallet not found"));

        WalletEntity receiverWallet =
                walletRepo.findByUserId(receiver.getId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Receiver wallet not found"));

        // A direct transfer is valid only when both wallets use
        // the same currency. Currency conversion requires a separate flow.
        if (!senderWallet.getCurrency()
                .equals(receiverWallet.getCurrency())) {
            throw new IllegalArgumentException(
                    "Wallet currencies do not match");
        }

        // Ensure the sender has enough funds before recording the debit.
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // Create the sender's debit transaction.
        TransactionEntity senderTx = new TransactionEntity();

        senderTx.setUser(sender);
        senderTx.setWallet(senderWallet);
        senderTx.setAmount(amount);
        senderTx.setType(TransactionType.DEBIT);
        senderTx.setStatus(TransactionStatus.PROCESSING);
        senderTx.setReferenceType("WALLET_TRANSFER");
        senderTx.setReferenceId(receiver.getId());
        senderTx.setIdempotencyKey(idempotencyKey);

        /*
         * Flush exposes database constraint violations immediately.
         * Do not catch every DataIntegrityViolationException here:
         * unrelated constraint failures must not be mistaken for
         * successful duplicate requests.
         *
         * Concurrent duplicate requests require database-enforced
         * uniqueness and deliberate conflict handling.
         */
        transactionRepo.saveAndFlush(senderTx);

        // Update both balances within the same transaction.
        senderWallet.setBalance(
                senderWallet.getBalance().subtract(amount));

        receiverWallet.setBalance(
                receiverWallet.getBalance().add(amount));

        walletRepo.save(senderWallet);
        walletRepo.save(receiverWallet);

        // Mark the sender's debit as completed.
        senderTx.setStatus(TransactionStatus.COMPLETED);
        transactionRepo.save(senderTx);

        // Record the matching credit in the receiver's transaction history.
        TransactionEntity receiverTx = new TransactionEntity();

        receiverTx.setUser(receiver);
        receiverTx.setWallet(receiverWallet);
        receiverTx.setAmount(amount);
        receiverTx.setType(TransactionType.CREDIT);
        receiverTx.setStatus(TransactionStatus.COMPLETED);
        receiverTx.setReferenceType("WALLET_TRANSFER");
        receiverTx.setReferenceId(sender.getId());

        transactionRepo.save(receiverTx);

        log.info("Wallet transfer completed. Transaction ID: {}",
                senderTx.getId());

        return senderTx;
    }

    /**
     * Handles a transfer after all configured optimistic-lock retries fail.
     *
     * @param exception the final optimistic-locking exception
     * @param senderEmail sender's email
     * @param receiverEmail receiver's email
     * @param amount transfer amount
     * @param idempotencyKey request idempotency key
     * @return never returns normally; propagates a failure to the caller
     */
    @Recover
    public TransactionEntity recover(
            ObjectOptimisticLockingFailureException exception,
            String senderEmail,
            String receiverEmail,
            BigDecimal amount,
            String idempotencyKey) {

        log.error(
                "Transfer failed after retries. Sender: {}, receiver: {}",
                senderEmail,
                receiverEmail,
                exception
        );

        throw new IllegalStateException(
                "Transfer failed after multiple retries.",
                exception
        );
    }

    /**
     * Ensures that the amount is present and greater than zero.
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero");
        }
    }

    /**
     * Ensures that an idempotency key is provided.
     */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required");
        }
    }
}

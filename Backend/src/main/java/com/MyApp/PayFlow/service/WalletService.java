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
import jakarta.transaction.Transactional;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepo walletRepo;
    private final UserRepository userRepository;
    private final TransactionRepo transactionRepo;

    private final PaymentProcessor paymentProcessor;

    private final ApplicationEventPublisher publisher;

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);


    public WalletBalanceResponse getWalletBalance(UUID userId){
        WalletEntity wallet = walletRepo.findByUserId(userId)
                .orElseThrow(()->new RuntimeException("Wallet not found"));

        return new WalletBalanceResponse(
                userId ,
                wallet.getBalance(),
                wallet.getCurrency()
        );
    }

    @Transactional
    public TransactionEntity  addMoney(String email, BigDecimal amount , String idempotencyKey) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }

        UserEntity user = userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("User with id is ont found?????"));
        // Check Idempotency
        Optional<TransactionEntity> existing =
                transactionRepo.findByIdempotencyKeyAndUserAndType(
                        idempotencyKey, user, TransactionType.CREDIT);

        if (existing.isPresent()) {
            return existing.get(); // Return old transaction, do NOT credit again
        }

        WalletEntity wallet = walletRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        TransactionEntity tx = new TransactionEntity();

        tx.setUser(user);
        tx.setWallet(wallet);
        tx.setAmount(amount);
        tx.setType(TransactionType.CREDIT);
        tx.setStatus(TransactionStatus.PROCESSING);
        tx.setReferenceType("ADD_MONEY");
        tx.setIdempotencyKey(idempotencyKey);

        transactionRepo.save(tx);

        //start Async processing
        publisher.publishEvent(new PaymentEvent(tx.getId()));

        return tx;

    }


    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public TransactionEntity transferMoney(String senderEmail, String receiverEmail, BigDecimal amount , String idempotencyKey) {

        int attempt = RetrySynchronizationManager.getContext().getRetryCount() + 1;
//        log.info("Transfer attempt {} by thread {}", attempt, Thread.currentThread().getName());

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }

        if (senderEmail.equals(receiverEmail)) {
            throw new RuntimeException("Cannot transfer to yourself");
        }

        UserEntity sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        UserEntity receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // ===============================
        //  IDEMPOTENCY CHECK (SENDER SIDE)
        // ===============================
        Optional<TransactionEntity> existing =
                transactionRepo.findByIdempotencyKeyAndUserAndType(
                        idempotencyKey,
                        sender,
                        TransactionType.DEBIT
                );

        if (existing.isPresent()) {
            return existing.get();   // Already processed
        }


        WalletEntity senderWallet = walletRepo.findByUserId(sender.getId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        WalletEntity receiverWallet = walletRepo.findByUserId(receiver.getId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        // Balance check
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // ===============================
        //  CREATE DEBIT TX FIRST (LOCK UNIQUE KEY)
        // ===============================

        TransactionEntity senderTx = new TransactionEntity();
        senderTx.setUser(sender);
        senderTx.setWallet(senderWallet);
        senderTx.setAmount(amount);
        senderTx.setType(TransactionType.DEBIT);
        senderTx.setStatus(TransactionStatus.PROCESSING);
        senderTx.setReferenceType("WALLET_TRANSFER");
        senderTx.setReferenceId(receiver.getId());
        senderTx.setIdempotencyKey(idempotencyKey);

        try {
            transactionRepo.saveAndFlush(senderTx);   // Flush to enforce unique constraint
        } catch (DataIntegrityViolationException ex) {
            // Duplicate request detected
            return transactionRepo
                    .findByIdempotencyKeyAndUserAndType(
                            idempotencyKey,
                            sender,
                            TransactionType.DEBIT)
                    .orElseThrow();
        }

        // ===============================
        // UPDATE WALLETS
        // ===============================

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));

        walletRepo.save(senderWallet);
        walletRepo.save(receiverWallet);

        // ===============================
        //  MARK DEBIT SUCCESS
        // ===============================

        senderTx.setStatus(TransactionStatus.COMPLETED);
        transactionRepo.save(senderTx);

        // ===============================
        //  CREATE RECEIVER CREDIT TX
        // (No idempotency needed here)
        // ===============================

        TransactionEntity receiverTx = new TransactionEntity();
        receiverTx.setUser(receiver);
        receiverTx.setWallet(receiverWallet);
        receiverTx.setAmount(amount);
        receiverTx.setType(TransactionType.CREDIT);
        receiverTx.setStatus(TransactionStatus.COMPLETED);
        receiverTx.setReferenceType("WALLET_TRANSFER");
        receiverTx.setReferenceId(sender.getId());

        transactionRepo.save(receiverTx);

        return senderTx;
    }

    @Recover
    public void recover(ObjectOptimisticLockingFailureException e,
                        String senderEmail,
                        String receiverEmail,
                        BigDecimal amount) {

        log.error("Transfer FAILED after retries by thread {}",
                Thread.currentThread().getName());

        throw new RuntimeException("Transfer failed after multiple retries.");
    }
}

package com.MyApp.PayFlow.service;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.ScheduledPaymentStatus;
import com.MyApp.PayFlow.entity.enums.TransactionStatus;
import com.MyApp.PayFlow.entity.enums.TransactionType;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.WalletRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class ScheduledPaymentWorker {

    @Autowired
    private ScheduledPaymentRepository scheduledPaymentRepository;

    @Autowired
    private TransactionRepo transactionRepo;

    @Autowired
    private WalletRepo walletRepo;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processScheduledPayments(){

        log.info("Scheduled Payment Worker started at {}", Instant.now());

        List<ScheduledPaymentEntity> payments =
                scheduledPaymentRepository.findPaymentsToExecute(Instant.now());

        log.info("Found {} payments ready for execution", payments.size());

        for(ScheduledPaymentEntity payment : payments){

            log.info("Processing paymentId={} sender={} receiver={} amount={}",
                    payment.getId(),
                    payment.getSender().getEmail(),
                    payment.getReceiver().getEmail(),
                    payment.getAmount());

            try{

                WalletEntity senderWallet = payment.getSenderWallet();
                WalletEntity receiverWallet = payment.getReceiverWallet();

                log.info("Sender balance before transaction: {}", senderWallet.getBalance());
                log.info("Receiver balance before transaction: {}", receiverWallet.getBalance());

                if(senderWallet.getBalance().compareTo(payment.getAmount()) < 0){

                    log.warn("Insufficient balance for paymentId={}", payment.getId());

                    payment.setStatus(ScheduledPaymentStatus.FAILED);
                    scheduledPaymentRepository.save(payment);
                    continue;
                }

                /* Update balances */

                senderWallet.setBalance(
                        senderWallet.getBalance().subtract(payment.getAmount())
                );

                receiverWallet.setBalance(
                        receiverWallet.getBalance().add(payment.getAmount())
                );

                walletRepo.save(senderWallet);
                walletRepo.save(receiverWallet);

                log.info("Balances updated successfully");

                /* 1️⃣ Debit Transaction */

                TransactionEntity debitTx = new TransactionEntity();

                debitTx.setUser(payment.getSender());
                debitTx.setWallet(senderWallet);
                debitTx.setAmount(payment.getAmount());
                debitTx.setType(TransactionType.DEBIT);
                debitTx.setStatus(TransactionStatus.COMPLETED);

                debitTx.setReferenceType("SCHEDULED_PAYMENT");
                debitTx.setReferenceId(payment.getId());

                transactionRepo.save(debitTx);

                log.info("Debit transaction created for paymentId={}", payment.getId());

                /* Credit Transaction */

                TransactionEntity creditTx = new TransactionEntity();

                creditTx.setUser(payment.getReceiver());
                creditTx.setWallet(receiverWallet);
                creditTx.setAmount(payment.getAmount());
                creditTx.setType(TransactionType.CREDIT);
                creditTx.setStatus(TransactionStatus.COMPLETED);

                creditTx.setReferenceType("SCHEDULED_PAYMENT");
                creditTx.setReferenceId(payment.getId());

                transactionRepo.save(creditTx);

                log.info("Credit transaction created for paymentId={}", payment.getId());

                /* Update Scheduled Payment */

                payment.setLastExecuted(Instant.now());
                payment.setStatus(ScheduledPaymentStatus.COMPLETED);

                scheduledPaymentRepository.save(payment);

                log.info("Scheduled payment completed successfully paymentId={}", payment.getId());

            }catch(Exception e){

                log.error("Error processing paymentId={} error={}",
                        payment.getId(), e.getMessage(), e);

                payment.setRetryCount(payment.getRetryCount() + 1);

                log.warn("Retry attempt {} for paymentId={}",
                        payment.getRetryCount(), payment.getId());

                if(payment.getRetryCount() >= 3){
                    payment.setStatus(ScheduledPaymentStatus.FAILED);
                    log.error("Payment marked FAILED after 3 retries paymentId={}", payment.getId());
                }

                scheduledPaymentRepository.save(payment);
            }
        }

        log.info("Scheduled Payment Worker finished at {}", Instant.now());
    }
}
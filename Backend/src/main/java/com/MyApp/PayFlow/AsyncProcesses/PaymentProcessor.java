package com.MyApp.PayFlow.AsyncProcesses;

import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.TransactionStatus;
import com.MyApp.PayFlow.events.PaymentEvent;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.WalletRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentProcessor {

    private final TransactionRepo transactionRepo;
    private final WalletRepo walletRepo;

    Logger logger = LoggerFactory.getLogger(PaymentProcessor.class);


    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processPayment(PaymentEvent event){

        UUID transactionId = event.transactionId();
        try{
            TransactionEntity tx = transactionRepo.findById(transactionId)
                    .orElseThrow();

            // simulate gateway delay
            Thread.sleep(5000);

            WalletEntity wallet = tx.getWallet();

            wallet.setBalance(
                    wallet.getBalance().add(tx.getAmount())
            );

            walletRepo.save(wallet);

            tx.setStatus(TransactionStatus.COMPLETED);

            transactionRepo.save(tx);
        }catch (Exception e) {

            TransactionEntity tx = transactionRepo.findById(transactionId)
                    .orElseThrow();

            tx.setStatus(TransactionStatus.FAILED);

            transactionRepo.save(tx);
        }
    }
}

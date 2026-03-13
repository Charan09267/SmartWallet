package com.MyApp.PayFlow.service;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.ScheduleType;
import com.MyApp.PayFlow.entity.enums.ScheduledPaymentStatus;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPaymentService {

    private final UserRepository userRepository;

    private final WalletRepo walletRepo;

    private final ScheduledPaymentRepository scheduledPaymentRepository;

    @Transactional
    public ScheduledPaymentEntity schedulePayment(
            String senderEmail,
            String receiverEmail,
            BigDecimal amount,
            String message,
            Instant scheduledAt
    ) {

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new RuntimeException("Invalid amount");
        }

        UserEntity sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        System.out.println("Reciever Email : "+receiverEmail);
        UserEntity receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new RuntimeException("Receiver not found...!!!"));

        WalletEntity senderWallet = walletRepo.findByUserId(sender.getId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        WalletEntity receiverWallet = walletRepo.findByUserId(receiver.getId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        ScheduledPaymentEntity payment = new ScheduledPaymentEntity();



        payment.setSender(sender);
        payment.setSenderWallet(senderWallet);

        payment.setReceiver(receiver);
        payment.setReceiverWallet(receiverWallet);

        payment.setAmount(amount);
        payment.setMessage(message);

        payment.setStatus(ScheduledPaymentStatus.ACTIVE);
        payment.setScheduleType(ScheduleType.ONE_TIME);

        payment.setScheduledAt(scheduledAt);
        payment.setNextExecution(scheduledAt);

        return scheduledPaymentRepository.save(payment);
    }

    @Transactional
    public void cancelScheduledPayment(UUID paymentId, String userEmail){

        log.info("Cancel request received for paymentId={}", paymentId);

        ScheduledPaymentEntity payment = scheduledPaymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Scheduled payment not found"));

        // Security check
        if(!payment.getSender().getEmail().equals(userEmail)){
            throw new RuntimeException("You are not allowed to cancel this payment");
        }

        // Check if already processed
        if(payment.getStatus() == ScheduledPaymentStatus.COMPLETED){
            throw new RuntimeException("Completed payments cannot be cancelled");
        }

        if(payment.getStatus() == ScheduledPaymentStatus.FAILED){
            throw new RuntimeException("Payment already cancelled");
        }

        payment.setStatus(ScheduledPaymentStatus.FAILED);

        scheduledPaymentRepository.save(payment);

        log.info("Scheduled payment cancelled successfully paymentId={}", paymentId);
    }
}

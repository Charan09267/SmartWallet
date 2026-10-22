
package com.MyApp.PayFlow.service.scheduledPayment;

import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodically finds scheduled payments that are due for execution.
 *
 * Each payment is delegated to a separate processor so that failures
 * can be handled independently.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledPaymentWorker {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final ScheduledPaymentProcessor scheduledPaymentProcessor;

    /**
     * Executes due payments every 30 seconds after the previous run finishes.
     */
    @Scheduled(fixedDelay = 30000)
    public void processScheduledPayments() {
        Instant now = Instant.now();

        log.info("Scheduled payment worker started at {}", now);

        List<ScheduledPaymentEntity> payments =
                scheduledPaymentRepository.findPaymentsToExecute(now);

        for (ScheduledPaymentEntity payment : payments) {
            try {
                log.info("Processing scheduled payment id={}", payment.getId());

                // Each call enters the processor's transaction separately.
                scheduledPaymentProcessor.processPayment(payment.getId());

            } catch (Exception ex) {
                log.error(
                        "Scheduled payment processing failed. paymentId={}",
                        payment.getId(),
                        ex
                );

                // Record failure after the processing transaction has rolled back.
                try {
                    scheduledPaymentProcessor.recordFailure(payment.getId());
                } catch (Exception failureEx) {
                    log.error(
                            "Could not record payment failure. paymentId={}",
                            payment.getId(),
                            failureEx
                    );
                }
            }
        }

        log.info("Scheduled payment worker finished at {}", Instant.now());
    }
}

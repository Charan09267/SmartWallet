package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.dto.ScheduledPaymentRequest;
import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.service.ScheduledPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/user/schedule-payment")
public class ScheduledPaymentController {

    @Autowired
    private ScheduledPaymentService scheduledPaymentService;

    /**
     * Schedules a payment from the authenticated user to a receiver.
     *
     * The sender's email comes from the authenticated principal rather
     * than being accepted from the request body.
     */
    @PostMapping
    public ResponseEntity<?> schedulePayment(
            @RequestBody ScheduledPaymentRequest request,
            Authentication authentication) {

        String senderEmail = authentication.getName();

        // Delegate payment validation and creation to the service layer.
        ScheduledPaymentEntity payment =
                scheduledPaymentService.schedulePayment(
                        senderEmail,
                        request.getReceiverEmail(),
                        request.getAmount(),
                        request.getMessage(),
                        request.getScheduledAt()
                );

        // Return the details needed by the client to track the scheduled payment.
        return ResponseEntity.ok(
                Map.of(
                        "scheduledPaymentId", payment.getId(),
                        "scheduledAt", payment.getScheduledAt(),
                        "status", payment.getStatus()
                )
        );
    }

    /**
     * Cancels a scheduled payment after the service validates ownership
     * and whether the payment is eligible for cancellation.
     *
     * @param id scheduled payment identifier
     * @param authentication authenticated user's principal
     * @return cancellation confirmation
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelScheduledPayment(
            @PathVariable UUID id,
            Authentication authentication) {

        String email = authentication.getName();

        scheduledPaymentService.cancelScheduledPayment(id, email);

        return ResponseEntity.ok("Scheduled payment cancelled successfully");
    }
}

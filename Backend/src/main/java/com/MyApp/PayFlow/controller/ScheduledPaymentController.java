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

    @PostMapping()
    public ResponseEntity<?> schedulePayment(
            @RequestBody ScheduledPaymentRequest request,
            Authentication authentication
    ){

        String senderEmail = authentication.getName();

        ScheduledPaymentEntity payment =
                scheduledPaymentService.schedulePayment(
                        senderEmail,
                        request.getReceiverEmail(),
                        request.getAmount(),
                        request.getMessage(),
                        request.getScheduledAt()
                );
        return ResponseEntity.ok(
                Map.of(
                        "scheduledPaymentId", payment.getId(),
                        "scheduledAt", payment.getScheduledAt(),
                        "status", payment.getStatus()
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelScheduledPayment(@PathVariable UUID id , Authentication authentication){

        String email = authentication.getName();

        scheduledPaymentService.cancelScheduledPayment(id, email);

        return ResponseEntity.ok("Scheduled payment cancelled successfully");
    }
}

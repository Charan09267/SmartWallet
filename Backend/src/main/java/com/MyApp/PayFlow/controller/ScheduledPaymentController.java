package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.dto.ScheduledPaymentRequest;
import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.service.ScheduledPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class ScheduledPaymentController {

    @Autowired
    private ScheduledPaymentService scheduledPaymentService;

    @PostMapping("/schedule-payment")
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
}

package com.MyApp.PayFlow.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class ScheduledPaymentRequest {
    private String receiverEmail;

    private BigDecimal amount;

    private String message;

    private Instant scheduledAt;
}

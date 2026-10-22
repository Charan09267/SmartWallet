package com.MyApp.PayFlow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ScheduledPaymentDto {
    private UUID id;
    private Instant createdAt;
    private Instant updatedAt;
    private BigDecimal amount;
    private Instant scheduledAt;
    private Instant lastExecuted;
    private Instant nextExecution;
    private String status;
    private String scheduleType;
}

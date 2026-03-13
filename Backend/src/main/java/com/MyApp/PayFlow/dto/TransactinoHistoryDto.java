package com.MyApp.PayFlow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactinoHistoryDto {
    private UUID transactionId;
    private BigDecimal amount;
    private String direction; // CREDIT / DEBIT
    private Instant createdAt;
}

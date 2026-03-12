package com.MyApp.PayFlow.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WalletBalanceResponse {

    private UUID userId;
    private BigDecimal balance;
    private String currency;
}

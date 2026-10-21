package com.MyApp.PayFlow.entity;


import com.MyApp.PayFlow.entity.enums.ScheduleType;
import com.MyApp.PayFlow.entity.enums.ScheduledPaymentStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Entity
@Table(name = "scheduled_payments")
@Getter
@Setter
public class ScheduledPaymentEntity extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @ManyToOne
    @JoinColumn(name = "sender_wallet_id", nullable = false)
    private WalletEntity senderWallet;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private UserEntity receiver;

    @ManyToOne
    @JoinColumn(name = "receiver_wallet_id", nullable = false)
    private WalletEntity receiverWallet;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String message;

    @Enumerated(EnumType.STRING)
    private ScheduledPaymentStatus status;

    @Enumerated(EnumType.STRING)
    private ScheduleType scheduleType;

    private Instant scheduledAt;

    private Instant lastExecuted;

    private Instant nextExecution;


    @Builder.Default
    @Column(nullable = false)
    private Integer retryCount = 0;

    @Version
    private int version;
}

package com.MyApp.PayFlow.entity;

import com.MyApp.PayFlow.entity.enums.TransactionStatus;
import com.MyApp.PayFlow.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"idempotency_key" ,"user_id", "type"})
        }
)
@Getter
@Setter
public class TransactionEntity extends BaseEntity{

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id" , nullable = false)
    private UserEntity user;


    @ManyToOne
    @JoinColumn(name = "wallet_id" , nullable = false)
    private WalletEntity wallet;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false , precision = 15 , scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String paymentGateway;

    private String gatewayTrasactionId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String referenceType;

    private UUID referenceId;

    @Version
    private int version;
}

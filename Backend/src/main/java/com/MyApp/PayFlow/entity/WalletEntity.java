package com.MyApp.PayFlow.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
public class WalletEntity extends BaseEntity{

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id" , nullable = false, unique = true)
    private UserEntity user;

    @Column(nullable = false , precision = 15 , scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;


    private String currency = "INR";

    @Version
    private int version;

    @OneToMany(mappedBy = "wallet")
    private List<TransactionEntity> transactions;
}

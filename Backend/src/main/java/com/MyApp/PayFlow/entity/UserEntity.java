package com.MyApp.PayFlow.entity;

import com.MyApp.PayFlow.entity.enums.AuthProvider;
import com.MyApp.PayFlow.entity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private WalletEntity wallet;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<TransactionEntity> transactions;

    // payments sent by this user
    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY)
    private List<ScheduledPaymentEntity> sentPayments;

    // payments received by this user
    @OneToMany(mappedBy = "receiver", fetch = FetchType.LAZY)
    private List<ScheduledPaymentEntity> receivedPayments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider; // LOCAL or GOOGLE
}

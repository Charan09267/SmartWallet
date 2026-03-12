package com.MyApp.PayFlow.repo;

import com.MyApp.PayFlow.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

@Repository
public interface WalletRepo extends JpaRepository<WalletEntity , UUID> {

    Optional<WalletEntity> findByUserId(UUID userId);
}

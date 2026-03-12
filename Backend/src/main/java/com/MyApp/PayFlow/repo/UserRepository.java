package com.MyApp.PayFlow.repo;

import com.MyApp.PayFlow.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.awt.print.Book;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity , UUID> {
    Optional<UserEntity> findByEmail(String email);

    Boolean existsByEmail(String email);
}

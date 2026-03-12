package com.MyApp.PayFlow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Column(name = "create_at" , nullable = false)
    private Instant createdAt = Instant.now();


    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate(){
        this.updatedAt = Instant.now();
    }
}

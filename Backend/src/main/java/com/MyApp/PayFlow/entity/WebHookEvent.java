package com.MyApp.PayFlow.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
public class WebHookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    private String gatewayName;

    @Column(unique = true)
    private String eventId;

    private String eventType;

    @Column(columnDefinition = "json")
    private String payload;

    private boolean processed = false;

    private Instant createdAt = Instant.now();
}

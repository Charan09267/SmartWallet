package com.MyApp.PayFlow.events;

import java.util.UUID;

public record PaymentEvent(UUID transactionId) {
}

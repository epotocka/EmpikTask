package com.example.empiktask.usage.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(String couponCode, UUID userId, Instant expiresAt) {
}

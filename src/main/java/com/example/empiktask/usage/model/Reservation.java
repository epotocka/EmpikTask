package com.example.empiktask.usage.model;

import java.time.Instant;
import java.util.UUID;

public record Reservation(String couponCode, UUID userId, Instant expiresAt) {
}

package com.example.empiktask.usage.dto;

import java.time.Instant;
import java.util.UUID;

public record CouponUsageResponse(String couponCode, UUID userId, Instant usedAt) {
}

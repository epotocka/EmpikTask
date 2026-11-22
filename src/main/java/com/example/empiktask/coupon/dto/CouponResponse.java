package com.example.empiktask.coupon.dto;

import java.time.Instant;
import java.util.UUID;

public record CouponResponse(UUID id, String code, String countryCode, Instant createdAt, int maxUsages,
        int usedCount) {
}

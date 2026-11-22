package com.example.empiktask.usage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReserveCouponRequest(
        @NotBlank @Size(max = COUPON_CODE_MAX_LENGTH)
        @Pattern(regexp = COUPON_CODE_PATTERN, message = COUPON_CODE_MESSAGE) String couponCode,
        @NotNull UUID userId) {

    private static final int COUPON_CODE_MAX_LENGTH = 64;
    private static final String COUPON_CODE_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String COUPON_CODE_MESSAGE = "may contain only letters, digits, '-' and '_'";
}

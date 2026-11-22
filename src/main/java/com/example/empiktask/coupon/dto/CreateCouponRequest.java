package com.example.empiktask.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCouponRequest(
        @NotBlank @Size(max = COUPON_CODE_MAX_LENGTH)
        @Pattern(regexp = COUPON_CODE_PATTERN, message = COUPON_CODE_MESSAGE) String code,
        @NotBlank @Pattern(regexp = COUNTRY_CODE_PATTERN, message = COUNTRY_CODE_MESSAGE) String countryCode,
        @NotNull @Min(1) Integer maxUsages) {

    private static final int COUPON_CODE_MAX_LENGTH = 64;
    private static final String COUPON_CODE_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String COUPON_CODE_MESSAGE = "may contain only letters, digits, '-' and '_'";
    private static final String COUNTRY_CODE_PATTERN = "^[A-Za-z]{2}$";
    private static final String COUNTRY_CODE_MESSAGE = "must be a two-letter ISO 3166-1 alpha-2 country code, e.g. PL";
}

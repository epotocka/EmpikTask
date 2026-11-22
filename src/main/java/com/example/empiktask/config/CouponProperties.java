package com.example.empiktask.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coupons")
public record CouponProperties(@Valid @NotNull Reservation reservation, @Valid @NotNull Geolocation geolocation) {

    public record Reservation(@NotNull Duration ttl) {
    }

    public record Geolocation(@NotBlank String baseUrl) {
    }
}

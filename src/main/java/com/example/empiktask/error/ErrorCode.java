package com.example.empiktask.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request"),
    COUNTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Country not found"),
    COUNTRY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Country already exists"),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Coupon not found"),
    COUPON_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Coupon code already exists"),
    COUPON_NOT_AVAILABLE_IN_COUNTRY(HttpStatus.FORBIDDEN, "Coupon not available in your country"),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "Coupon already used"),
    COUPON_USAGE_LIMIT_REACHED(HttpStatus.CONFLICT, "Coupon usage limit reached"),
    COUPON_TEMPORARILY_UNAVAILABLE(HttpStatus.CONFLICT, "Coupon temporarily unavailable"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Reservation not found"),
    COUNTRY_NOT_RESOLVABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Country could not be determined"),
    GEOLOCATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Geolocation service unavailable"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data conflict");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}

package com.example.empiktask.geolocation.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CountryNotResolvableException extends BusinessException {

    public CountryNotResolvableException(String ipAddress) {
        super(ErrorCode.COUNTRY_NOT_RESOLVABLE,
                "We could not determine your country from IP address '%s', so the coupon cannot be used."
                        .formatted(ipAddress));
    }
}

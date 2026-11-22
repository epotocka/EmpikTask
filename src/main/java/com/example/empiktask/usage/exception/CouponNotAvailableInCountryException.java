package com.example.empiktask.usage.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponNotAvailableInCountryException extends BusinessException {

    public CouponNotAvailableInCountryException(String couponCode, String userCountryCode) {
        super(ErrorCode.COUPON_NOT_AVAILABLE_IN_COUNTRY,
                "Coupon '%s' is not available in your country (%s).".formatted(couponCode, userCountryCode));
    }
}

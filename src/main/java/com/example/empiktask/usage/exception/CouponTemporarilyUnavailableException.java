package com.example.empiktask.usage.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponTemporarilyUnavailableException extends BusinessException {

    public CouponTemporarilyUnavailableException(String couponCode) {
        super(ErrorCode.COUPON_TEMPORARILY_UNAVAILABLE,
                "All remaining uses of coupon '%s' are currently reserved by other users. Please try again in a few minutes."
                        .formatted(couponCode));
    }
}

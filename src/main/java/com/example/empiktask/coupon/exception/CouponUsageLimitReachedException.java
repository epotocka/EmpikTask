package com.example.empiktask.coupon.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponUsageLimitReachedException extends BusinessException {

    public CouponUsageLimitReachedException(String code) {
        super(ErrorCode.COUPON_USAGE_LIMIT_REACHED,
                "Sorry, coupon '%s' has already been used the maximum number of times and is no longer available."
                        .formatted(code));
    }
}

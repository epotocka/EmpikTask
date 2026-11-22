package com.example.empiktask.usage.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponAlreadyUsedException extends BusinessException {

    public CouponAlreadyUsedException(String couponCode) {
        super(ErrorCode.COUPON_ALREADY_USED,
                "You have already used coupon '%s'. Each coupon can be used only once per user.".formatted(couponCode));
    }
}

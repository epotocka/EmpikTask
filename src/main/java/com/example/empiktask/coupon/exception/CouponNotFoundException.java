package com.example.empiktask.coupon.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponNotFoundException extends BusinessException {

    public CouponNotFoundException(String code) {
        super(ErrorCode.COUPON_NOT_FOUND, "Coupon '%s' does not exist.".formatted(code));
    }
}

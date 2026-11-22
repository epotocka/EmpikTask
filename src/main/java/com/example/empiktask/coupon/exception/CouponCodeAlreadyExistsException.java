package com.example.empiktask.coupon.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CouponCodeAlreadyExistsException extends BusinessException {

    public CouponCodeAlreadyExistsException(String code) {
        super(ErrorCode.COUPON_CODE_ALREADY_EXISTS,
                "A coupon with code '%s' already exists. Coupon codes are case-insensitive.".formatted(code));
    }
}

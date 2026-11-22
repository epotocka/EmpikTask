package com.example.empiktask.usage.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class ReservationNotFoundException extends BusinessException {

    public ReservationNotFoundException(String couponCode) {
        super(ErrorCode.RESERVATION_NOT_FOUND,
                "You have no active reservation for coupon '%s'. It may have expired, please reserve the coupon again."
                        .formatted(couponCode));
    }
}

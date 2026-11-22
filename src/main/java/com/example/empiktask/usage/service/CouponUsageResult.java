package com.example.empiktask.usage.service;

import com.example.empiktask.usage.model.UsedCoupon;

public record CouponUsageResult(UsedCoupon usedCoupon, boolean created) {

    static CouponUsageResult created(UsedCoupon usedCoupon) {
        return new CouponUsageResult(usedCoupon, true);
    }

    static CouponUsageResult existing(UsedCoupon usedCoupon) {
        return new CouponUsageResult(usedCoupon, false);
    }
}

package com.example.empiktask.usage.controller;

import com.example.empiktask.usage.dto.CouponUsageResponse;
import com.example.empiktask.usage.dto.ReservationResponse;
import com.example.empiktask.usage.dto.ReserveCouponRequest;
import com.example.empiktask.usage.dto.UseCouponRequest;
import com.example.empiktask.usage.model.Reservation;
import com.example.empiktask.usage.model.UsedCoupon;
import com.example.empiktask.usage.service.CouponUsageResult;
import com.example.empiktask.usage.service.CouponUsageService;
import com.example.empiktask.usage.service.ReservationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponUsageController {

    private final CouponUsageService couponUsageService;

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveCouponRequest request,
            HttpServletRequest httpRequest) {
        ReservationResult result = couponUsageService.reserve(request.couponCode(), request.userId(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.status(statusFor(result.created())).body(toResponse(result.reservation()));
    }

    @PostMapping("/coupon-usages")
    public ResponseEntity<CouponUsageResponse> useCoupon(@Valid @RequestBody UseCouponRequest request) {
        CouponUsageResult result = couponUsageService.useCoupon(request.couponCode(), request.userId());
        return ResponseEntity.status(statusFor(result.created())).body(toResponse(result.usedCoupon()));
    }

    private static HttpStatus statusFor(boolean created) {
        return created ? HttpStatus.CREATED : HttpStatus.OK;
    }

    private static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(reservation.couponCode(), reservation.userId(), reservation.expiresAt());
    }

    private static CouponUsageResponse toResponse(UsedCoupon usedCoupon) {
        return new CouponUsageResponse(usedCoupon.getCoupon().getCode(), usedCoupon.getUserId(),
                usedCoupon.getUsedAt());
    }
}

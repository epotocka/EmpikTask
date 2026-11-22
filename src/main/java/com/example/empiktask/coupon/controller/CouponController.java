package com.example.empiktask.coupon.controller;

import com.example.empiktask.coupon.dto.CouponResponse;
import com.example.empiktask.coupon.dto.CreateCouponRequest;
import com.example.empiktask.coupon.model.Coupon;
import com.example.empiktask.coupon.service.CouponService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        Coupon created = couponService.create(request.code(), request.countryCode(), request.maxUsages());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(created.getCode())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping("/{code}")
    public CouponResponse findByCode(@PathVariable String code) {
        return toResponse(couponService.findByCode(code));
    }

    private static CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getCountryCode(), coupon.getCreatedAt(),
                coupon.getMaxUsages(), coupon.getUsedCount());
    }
}

package com.example.empiktask.coupon.service;

import com.example.empiktask.country.exception.CountryNotFoundException;
import com.example.empiktask.country.repository.CountryRepository;
import com.example.empiktask.coupon.exception.CouponCodeAlreadyExistsException;
import com.example.empiktask.coupon.exception.CouponNotFoundException;
import com.example.empiktask.coupon.model.Coupon;
import com.example.empiktask.coupon.repository.CouponRepository;
import com.example.empiktask.util.CountryCodesUtil;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final CountryRepository countryRepository;
    private final Clock clock;

    @Transactional
    public Coupon create(String code, String countryCode, int maxUsages) {
        String trimmedCode = code.trim();
        String normalizedCountryCode = CountryCodesUtil.normalize(countryCode);
        if (!countryRepository.existsById(normalizedCountryCode)) {
            throw new CountryNotFoundException(normalizedCountryCode);
        }
        UUID id = UUID.randomUUID();
        int inserted = couponRepository.insertIfCodeAbsent(id, trimmedCode, normalizedCountryCode, clock.instant(),
                maxUsages);
        if (inserted == 0) {
            throw new CouponCodeAlreadyExistsException(trimmedCode);
        }
        return couponRepository.findWithCountryById(id).orElseThrow();
    }

    public Coupon findByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new CouponNotFoundException(code));
    }
}

package com.example.empiktask.coupon.model;

import com.example.empiktask.country.model.Country;
import com.example.empiktask.coupon.exception.CouponUsageLimitReachedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "coupon_code", length = 64, nullable = false, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false, updatable = false)
    private Country country;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "max_usages", nullable = false, updatable = false)
    private int maxUsages;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    public String getCountryCode() {
        return country.getCode();
    }

    public boolean isUsageLimitReached() {
        return usedCount >= maxUsages;
    }

    public boolean hasCapacityFor(long activeReservations) {
        return usedCount + activeReservations < maxUsages;
    }

    public void registerUsage() {
        if (isUsageLimitReached()) {
            throw new CouponUsageLimitReachedException(code);
        }
        usedCount++;
    }
}

package com.example.empiktask.usage.model;

import com.example.empiktask.coupon.model.Coupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "used_coupons_by_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false, updatable = false)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    public UsedCoupon(Coupon coupon, UUID userId, Instant usedAt) {
        this.coupon = coupon;
        this.userId = userId;
        this.usedAt = usedAt;
    }
}

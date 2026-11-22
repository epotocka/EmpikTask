package com.example.empiktask.usage.repository;

import com.example.empiktask.usage.model.UsedCoupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedCouponRepository extends JpaRepository<UsedCoupon, UUID> {

    boolean existsByCouponIdAndUserId(UUID couponId, UUID userId);

    @EntityGraph(attributePaths = "coupon")
    Optional<UsedCoupon> findByCouponIdAndUserId(UUID couponId, UUID userId);
}

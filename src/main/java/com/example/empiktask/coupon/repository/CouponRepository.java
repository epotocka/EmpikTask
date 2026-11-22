package com.example.empiktask.coupon.repository;

import com.example.empiktask.coupon.model.Coupon;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    @EntityGraph(attributePaths = "country")
    Optional<Coupon> findByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "country")
    Optional<Coupon> findWithCountryById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO coupon (id, coupon_code, country_code, created_at, max_usages, used_count)
            VALUES (:id, :code, :countryCode, :createdAt, :maxUsages, 0)
            ON CONFLICT ((upper(coupon_code))) DO NOTHING
            """, nativeQuery = true)
    int insertIfCodeAbsent(@Param("id") UUID id, @Param("code") String code,
            @Param("countryCode") String countryCode, @Param("createdAt") Instant createdAt,
            @Param("maxUsages") int maxUsages);
}

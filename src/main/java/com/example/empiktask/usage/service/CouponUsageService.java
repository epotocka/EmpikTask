package com.example.empiktask.usage.service;

import com.example.empiktask.config.CouponProperties;
import com.example.empiktask.coupon.exception.CouponNotFoundException;
import com.example.empiktask.coupon.exception.CouponUsageLimitReachedException;
import com.example.empiktask.coupon.model.Coupon;
import com.example.empiktask.coupon.repository.CouponRepository;
import com.example.empiktask.geolocation.client.GeolocationClient;
import com.example.empiktask.usage.exception.CouponAlreadyUsedException;
import com.example.empiktask.usage.exception.CouponNotAvailableInCountryException;
import com.example.empiktask.usage.exception.CouponTemporarilyUnavailableException;
import com.example.empiktask.usage.exception.ReservationNotFoundException;
import com.example.empiktask.usage.model.Reservation;
import com.example.empiktask.usage.model.UsedCoupon;
import com.example.empiktask.usage.repository.ReservationStore;
import com.example.empiktask.usage.repository.UsedCouponRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponUsageService {

    private final CouponRepository couponRepository;
    private final UsedCouponRepository usedCouponRepository;
    private final ReservationStore reservationStore;
    private final GeolocationClient geolocationClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final CouponProperties properties;

    public ReservationResult reserve(String couponCode, UUID userId, String clientIp) {
        Coupon coupon = findCoupon(couponCode);
        Optional<ReservationResult> existing = findExistingReservationOrValidateNew(coupon, userId, clock.instant());
        if (existing.isPresent()) {
            log.info("Coupon {} reservation for user {} already exists, returning it", coupon.getCode(), userId);
            return existing.get();
        }
        verifyUserCountry(coupon, clientIp);
        ReservationResult result = transactionTemplate.execute(status -> reserveUnderLock(coupon.getId(), userId));
        log.info("Coupon {} reserved for user {} until {}", coupon.getCode(), userId,
                result.reservation().expiresAt());
        return result;
    }

    public CouponUsageResult useCoupon(String couponCode, UUID userId) {
        Coupon coupon = findCoupon(couponCode);
        Optional<UsedCoupon> previousUsage = usedCouponRepository.findByCouponIdAndUserId(coupon.getId(), userId);
        if (previousUsage.isPresent()) {
            log.info("Coupon {} already used by user {}, returning the original usage", coupon.getCode(), userId);
            return CouponUsageResult.existing(previousUsage.get());
        }
        requireActiveReservation(coupon, userId, clock.instant());
        CouponUsageResult result = transactionTemplate.execute(status -> registerUsageUnderLock(coupon.getId(), userId));
        reservationStore.remove(coupon.getId(), userId);
        log.info("Coupon {} used by user {}", coupon.getCode(), userId);
        return result;
    }

    private ReservationResult reserveUnderLock(UUID couponId, UUID userId) {
        Coupon coupon = lockCoupon(couponId);
        Instant now = clock.instant();
        Optional<ReservationResult> existing = findExistingReservationOrValidateNew(coupon, userId, now);
        if (existing.isPresent()) {
            return existing.get();
        }
        Duration reservationTtl = properties.reservation().ttl();
        Instant expiresAt = now.plus(reservationTtl);
        reservationStore.upsertUserReservation(userId, couponId, expiresAt, now, reservationTtl)
                .ifPresent(releasedCouponId -> log.info("User {} switched reservation, released coupon {}",
                        userId, releasedCouponId));
        return ReservationResult.created(new Reservation(coupon.getCode(), userId, expiresAt));
    }

    private CouponUsageResult registerUsageUnderLock(UUID couponId, UUID userId) {
        Coupon coupon = lockCoupon(couponId);
        // re-checked under the lock: a parallel duplicate may have recorded the usage after useCoupon()'s early check
        Optional<UsedCoupon> previousUsage = usedCouponRepository.findByCouponIdAndUserId(couponId, userId);
        if (previousUsage.isPresent()) {
            return CouponUsageResult.existing(previousUsage.get());
        }
        Instant now = clock.instant();
        requireActiveReservation(coupon, userId, now);
        coupon.registerUsage();
        return CouponUsageResult.created(usedCouponRepository.save(new UsedCoupon(coupon, userId, now)));
    }

    /**
     * Returns the user's active reservation for the coupon if one exists. Otherwise validates that a new
     * reservation is allowed and returns an empty result.
     * <p>
     * Called twice per reservation: first without a lock to fail fast, then under the coupon's row lock,
     * where the result is authoritative because no concurrent request can change it.
     *
     * @throws CouponAlreadyUsedException if the user has already used the coupon
     * @throws CouponUsageLimitReachedException if the coupon has been used the maximum number of times
     * @throws CouponTemporarilyUnavailableException if all remaining uses are currently reserved
     */
    private Optional<ReservationResult> findExistingReservationOrValidateNew(Coupon coupon, UUID userId, Instant now) {
        verifyNotUsedBy(coupon, userId);
        Optional<ReservationResult> existing = reservationStore.findActiveExpiry(coupon.getId(), userId, now)
                .map(expiresAt -> ReservationResult.existing(new Reservation(coupon.getCode(), userId, expiresAt)));
        if (existing.isEmpty()) {
            verifyCapacity(coupon, now);
        }
        return existing;
    }

    private void verifyCapacity(Coupon coupon, Instant now) {
        if (coupon.isUsageLimitReached()) {
            throw new CouponUsageLimitReachedException(coupon.getCode());
        }
        if (!coupon.hasCapacityFor(reservationStore.countActive(coupon.getId(), now))) {
            throw new CouponTemporarilyUnavailableException(coupon.getCode());
        }
    }

    private void verifyNotUsedBy(Coupon coupon, UUID userId) {
        if (usedCouponRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new CouponAlreadyUsedException(coupon.getCode());
        }
    }

    private void verifyUserCountry(Coupon coupon, String clientIp) {
        String userCountryCode = geolocationClient.countryCodeOf(clientIp);
        if (!coupon.getCountryCode().equals(userCountryCode)) {
            throw new CouponNotAvailableInCountryException(coupon.getCode(), userCountryCode);
        }
    }

    private void requireActiveReservation(Coupon coupon, UUID userId, Instant now) {
        if (reservationStore.findActiveExpiry(coupon.getId(), userId, now).isEmpty()) {
            throw new ReservationNotFoundException(coupon.getCode());
        }
    }

    private Coupon findCoupon(String couponCode) {
        return couponRepository.findByCodeIgnoreCase(couponCode.trim())
                .orElseThrow(() -> new CouponNotFoundException(couponCode));
    }

    private Coupon lockCoupon(UUID couponId) {
        return couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId.toString()));
    }
}

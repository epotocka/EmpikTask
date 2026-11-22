package com.example.empiktask.usage.service;

import static com.example.empiktask.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.empiktask.coupon.exception.CouponUsageLimitReachedException;
import com.example.empiktask.support.IntegrationTest;
import com.example.empiktask.usage.exception.CouponTemporarilyUnavailableException;
import com.example.empiktask.usage.repository.ReservationStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Coupon usage under concurrent load")
class CouponUsageConcurrencyIntegrationTest extends IntegrationTest {

    private static final String COUPON = "RUSH";
    private static final int MAX_USAGES = 5;
    private static final int CONCURRENT_USERS = 40;
    private static final int PARALLEL_RETRIES = 10;

    @Autowired
    private CouponUsageService couponUsageService;

    @Autowired
    private ReservationStore reservationStore;

    @BeforeEach
    void setUpCoupon() {
        createCountry("PL", "Poland");
        createCoupon(COUPON, "PL", MAX_USAGES);
    }

    @Test
    @DisplayName("grants exactly maxUsages reservations when many users race for one coupon")
    void grantsExactlyMaxUsagesReservations() throws Exception {
        List<Outcome<ReservationResult>> outcomes = runConcurrently(randomUsers(CONCURRENT_USERS).stream()
                .<Callable<Outcome<ReservationResult>>>map(user ->
                        () -> attempt(() -> couponUsageService.reserve(COUPON, user, POLISH_IP)))
                .toList());

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(MAX_USAGES)
                .allSatisfy(outcome -> assertThat(outcome.value().created()).isTrue());
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .hasSize(CONCURRENT_USERS - MAX_USAGES)
                .allSatisfy(outcome -> assertThat(outcome.failure())
                        .isInstanceOf(CouponTemporarilyUnavailableException.class));
        assertThat(reservationStore.countActive(couponId(), clock.instant())).isEqualTo(MAX_USAGES);
    }

    @Test
    @DisplayName("never exceeds maxUsages when reservations and coupon usages interleave")
    void neverExceedsMaxUsagesWhenUsagesInterleave() throws Exception {
        List<Outcome<CouponUsageResult>> outcomes = runConcurrently(randomUsers(CONCURRENT_USERS).stream()
                .<Callable<Outcome<CouponUsageResult>>>map(user -> () -> attempt(() -> {
                    couponUsageService.reserve(COUPON, user, POLISH_IP);
                    return couponUsageService.useCoupon(COUPON, user);
                }))
                .toList());

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(MAX_USAGES);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.failure()).isInstanceOfAny(
                        CouponTemporarilyUnavailableException.class, CouponUsageLimitReachedException.class));
        assertThat(usedCount(COUPON)).isEqualTo(MAX_USAGES);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM used_coupons_by_user", Integer.class))
                .isEqualTo(MAX_USAGES);
        assertThat(reservationStore.countActive(couponId(), clock.instant())).isZero();
    }

    @Test
    @DisplayName("creates one reservation and returns it to every parallel retry by the same user")
    void parallelReservationRetriesShareOneReservation() throws Exception {
        UUID user = UUID.randomUUID();

        List<Outcome<ReservationResult>> outcomes = runConcurrently(IntStream.range(0, PARALLEL_RETRIES)
                .<Callable<Outcome<ReservationResult>>>mapToObj(i ->
                        () -> attempt(() -> couponUsageService.reserve(COUPON, user, POLISH_IP)))
                .toList());

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).filteredOn(outcome -> outcome.value().created()).hasSize(1);
        assertThat(outcomes).extracting(outcome -> outcome.value().reservation()).containsOnly(
                outcomes.getFirst().value().reservation());
        assertThat(reservationStore.countActive(couponId(), clock.instant())).isEqualTo(1);
    }

    @Test
    @DisplayName("leaves a user with exactly one reservation when they reserve different coupons in parallel")
    void parallelReservationsOfDifferentCouponsLeaveOneReservation() throws Exception {
        List<String> coupons = List.of(COUPON, "RUSH-2", "RUSH-3", "RUSH-4", "RUSH-5");
        coupons.stream().skip(1).forEach(code -> createCoupon(code, "PL", MAX_USAGES));
        UUID user = UUID.randomUUID();

        List<Outcome<ReservationResult>> outcomes = runConcurrently(IntStream.range(0, coupons.size() * 2)
                .<Callable<Outcome<ReservationResult>>>mapToObj(i -> () -> attempt(
                        () -> couponUsageService.reserve(coupons.get(i % coupons.size()), user, POLISH_IP)))
                .toList());

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        long activeReservations = coupons.stream()
                .mapToLong(code -> reservationStore.countActive(couponId(code), clock.instant()))
                .sum();
        assertThat(activeReservations).isEqualTo(1);
    }

    @Test
    @DisplayName("records one usage and returns it to every parallel usage retry by the same user")
    void parallelUsageRetriesShareOneUsage() throws Exception {
        UUID user = UUID.randomUUID();
        couponUsageService.reserve(COUPON, user, POLISH_IP);

        List<Outcome<CouponUsageResult>> outcomes = runConcurrently(IntStream.range(0, PARALLEL_RETRIES)
                .<Callable<Outcome<CouponUsageResult>>>mapToObj(i ->
                        () -> attempt(() -> couponUsageService.useCoupon(COUPON, user)))
                .toList());

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).filteredOn(outcome -> outcome.value().created()).hasSize(1);
        assertThat(outcomes).extracting(outcome -> outcome.value().usedCoupon().getId())
                .containsOnly(outcomes.getFirst().value().usedCoupon().getId());
        assertThat(usedCount(COUPON)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM used_coupons_by_user", Integer.class))
                .isEqualTo(1);
    }

    private static <T> Outcome<T> attempt(Supplier<T> action) {
        try {
            return new Outcome<>(action.get(), null);
        } catch (RuntimeException exception) {
            return new Outcome<>(null, exception);
        }
    }

    private static List<UUID> randomUsers(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private UUID couponId() {
        return couponId(COUPON);
    }

    private UUID couponId(String couponCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM coupon WHERE coupon_code = ?", UUID.class, couponCode);
    }

    private record Outcome<T>(T value, RuntimeException failure) {

        boolean succeeded() {
            return failure == null;
        }
    }
}

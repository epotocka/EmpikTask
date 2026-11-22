package com.example.empiktask.usage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.empiktask.geolocation.exception.CountryNotResolvableException;
import com.example.empiktask.geolocation.exception.GeolocationUnavailableException;
import com.example.empiktask.support.IntegrationTest;
import com.example.empiktask.usage.repository.ReservationStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@DisplayName("Coupon usage flow")
class CouponUsageControllerIntegrationTest extends IntegrationTest {

    private static final String COUPON = "SUMMER";

    @Autowired
    private ReservationStore reservationStore;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUpCoupon() {
        createCountry("PL", "Poland");
        createCountry("DE", "Germany");
        createCoupon(COUPON, "PL", 2);
    }

    @Nested
    @DisplayName("when reserving a coupon")
    class Reserving {

        @Test
        @DisplayName("creates a 15 minute reservation in Redis")
        void createsReservation() {
            MvcTestResult result = reserve("summer", userId, POLISH_IP);

            Instant expectedExpiry = clock.instant().plus(Duration.ofMinutes(15));
            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().extractingPath("$.couponCode").isEqualTo(COUPON);
            assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(userId.toString());
            assertThat(result).bodyJson().extractingPath("$.expiresAt").isEqualTo(expectedExpiry.toString());
            assertThat(reservationStore.findActiveExpiry(couponId(), userId, clock.instant()))
                    .contains(expectedExpiry);
            assertThat(usedCount(COUPON)).isZero();
        }

        @Test
        @DisplayName("resolves the user's country from the forwarded client IP")
        void usesForwardedClientIp() {
            reserve(COUPON, userId, POLISH_IP);

            then(geolocationClient).should().countryCodeOf(POLISH_IP);
        }

        @Test
        @DisplayName("rejects a user from another country")
        void rejectsUserFromAnotherCountry() {
            MvcTestResult result = reserve(COUPON, userId, GERMAN_IP);

            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.errorCode")
                    .isEqualTo("COUPON_NOT_AVAILABLE_IN_COUNTRY");
            assertThat(result).bodyJson().extractingPath("$.detail")
                    .isEqualTo("Coupon 'SUMMER' is not available in your country (DE).");
            assertThat(reservationStore.countActive(couponId(), clock.instant())).isZero();
        }

        @Test
        @DisplayName("returns 422 when the country cannot be determined from the IP")
        void rejectsUnresolvableCountry() {
            given(geolocationClient.countryCodeOf("10.0.0.1"))
                    .willThrow(new CountryNotResolvableException("10.0.0.1"));

            MvcTestResult result = reserve(COUPON, userId, "10.0.0.1");

            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUNTRY_NOT_RESOLVABLE");
        }

        @Test
        @DisplayName("returns 503 when the geolocation service is down")
        void reportsGeolocationOutage() {
            given(geolocationClient.countryCodeOf(POLISH_IP))
                    .willThrow(new GeolocationUnavailableException(new IllegalStateException("timeout")));

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("GEOLOCATION_UNAVAILABLE");
        }

        @Test
        @DisplayName("returns 404 for an unknown coupon without calling geolocation")
        void rejectsUnknownCouponFast() {
            MvcTestResult result = reserve("UNKNOWN", userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_NOT_FOUND");
            then(geolocationClient).should(never()).countryCodeOf(anyString());
        }

        @Test
        @DisplayName("returns the existing reservation with 200 when the same user retries")
        void returnsExistingReservationOnRetry() {
            Instant originalExpiry = clock.instant().plus(Duration.ofMinutes(15));
            reserve(COUPON, userId, POLISH_IP);
            clock.advance(Duration.ofMinutes(5));

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.expiresAt").isEqualTo(originalExpiry.toString());
            assertThat(reservationStore.countActive(couponId(), clock.instant())).isEqualTo(1);
            then(geolocationClient).should(times(1)).countryCodeOf(POLISH_IP);
        }

        @Test
        @DisplayName("returns the existing reservation on retry even when no free slots are left")
        void returnsExistingReservationWhenCouponIsFullyReserved() {
            reserve(COUPON, userId, POLISH_IP);
            reserve(COUPON, UUID.randomUUID(), POLISH_IP);

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(userId.toString());
            assertThat(reservationStore.countActive(couponId(), clock.instant())).isEqualTo(2);
        }

        @Test
        @DisplayName("rejects a user who has already used the coupon")
        void rejectsUserWhoAlreadyUsedCoupon() {
            reserve(COUPON, userId, POLISH_IP);
            useCoupon(COUPON, userId);

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_ALREADY_USED");
            assertThat(result).bodyJson().extractingPath("$.detail")
                    .isEqualTo("You have already used coupon 'SUMMER'. Each coupon can be used only once per user.");
        }

        @Test
        @DisplayName("returns a friendly message when the coupon was used the maximum number of times")
        void rejectsExhaustedCoupon() {
            reserveAndUseCoupon(UUID.randomUUID());
            reserveAndUseCoupon(UUID.randomUUID());

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_USAGE_LIMIT_REACHED");
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(
                    "Sorry, coupon 'SUMMER' has already been used the maximum number of times "
                            + "and is no longer available.");
        }

        @Test
        @DisplayName("rejects a reservation when used count plus active reservations reach the limit")
        void rejectsWhenRemainingUsagesAreReserved() {
            reserveAndUseCoupon(UUID.randomUUID());
            reserve(COUPON, UUID.randomUUID(), POLISH_IP);

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_TEMPORARILY_UNAVAILABLE");
        }

        @Test
        @DisplayName("frees capacity once other reservations expire")
        void expiredReservationsFreeCapacity() {
            reserve(COUPON, UUID.randomUUID(), POLISH_IP);
            reserve(COUPON, UUID.randomUUID(), POLISH_IP);
            clock.advance(Duration.ofMinutes(15).plusSeconds(1));

            MvcTestResult result = reserve(COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(reservationStore.countActive(couponId(), clock.instant())).isEqualTo(1);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource(delimiter = '|', value = {
                "missing user id      | {\"couponCode\": \"SUMMER\"}                                   | userId",
                "missing coupon code  | {\"userId\": \"5f2b8a7e-1c3d-4e5f-8a9b-0c1d2e3f4a5b\"}           | couponCode",
                "invalid coupon code  | {\"couponCode\": \"SUM MER\", \"userId\": \"5f2b8a7e-1c3d-4e5f-8a9b-0c1d2e3f4a5b\"} | couponCode"
        })
        @DisplayName("rejects invalid reservation payloads with field details")
        void rejectsInvalidPayload(String scenario, String json, String invalidField) {
            MvcTestResult result = postJson("/api/v1/reservations", json);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.fieldErrors." + invalidField).isNotNull();
        }
    }

    @Nested
    @DisplayName("when reserving another coupon while holding a reservation")
    class SwitchingReservation {

        private static final String OTHER_COUPON = "WINTER";

        @Test
        @DisplayName("moves the reservation to the new coupon and frees the old one")
        void movesReservationToNewCoupon() {
            createCoupon(OTHER_COUPON, "PL", 1);
            reserve(COUPON, userId, POLISH_IP);

            MvcTestResult result = reserve(OTHER_COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().extractingPath("$.couponCode").isEqualTo(OTHER_COUPON);
            assertThat(reservationStore.countActive(couponId(), clock.instant())).isZero();
            assertThat(reservationStore.countActive(couponId(OTHER_COUPON), clock.instant())).isEqualTo(1);
        }

        @Test
        @DisplayName("lets the user use only the new coupon")
        void onlyNewCouponCanBeUsed() {
            createCoupon(OTHER_COUPON, "PL", 1);
            reserve(COUPON, userId, POLISH_IP);
            reserve(OTHER_COUPON, userId, POLISH_IP);

            assertThat(useCoupon(COUPON, userId)).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(useCoupon(OTHER_COUPON, userId)).hasStatus(HttpStatus.CREATED);
            assertThat(usedCount(COUPON)).isZero();
            assertThat(usedCount(OTHER_COUPON)).isEqualTo(1);
        }

        @Test
        @DisplayName("makes the freed slot available to other users immediately")
        void freedSlotIsAvailableToOthers() {
            createCoupon(OTHER_COUPON, "PL", 1);
            reserve(COUPON, userId, POLISH_IP);
            reserve(COUPON, UUID.randomUUID(), POLISH_IP);
            reserve(OTHER_COUPON, userId, POLISH_IP);

            MvcTestResult result = reserve(COUPON, UUID.randomUUID(), POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("keeps the existing reservation when the new coupon cannot be reserved")
        void keepsReservationWhenSwitchFails() {
            createCoupon(OTHER_COUPON, "PL", 1);
            reserve(OTHER_COUPON, UUID.randomUUID(), POLISH_IP);
            reserve(COUPON, userId, POLISH_IP);

            MvcTestResult result = reserve(OTHER_COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_TEMPORARILY_UNAVAILABLE");
            assertThat(reservationStore.findActiveExpiry(couponId(), userId, clock.instant())).isPresent();
        }

        @Test
        @DisplayName("keeps the existing reservation when the new coupon is for another country")
        void keepsReservationWhenCountryDoesNotMatch() {
            createCoupon(OTHER_COUPON, "DE", 1);
            reserve(COUPON, userId, POLISH_IP);

            MvcTestResult result = reserve(OTHER_COUPON, userId, POLISH_IP);

            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(reservationStore.findActiveExpiry(couponId(), userId, clock.instant())).isPresent();
        }
    }

    @Nested
    @DisplayName("when using a reserved coupon")
    class UsingCoupon {

        @Test
        @DisplayName("moves the reservation from Redis to the database")
        void finalizesReservation() {
            reserve(COUPON, userId, POLISH_IP);

            MvcTestResult result = useCoupon("Summer", userId);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().extractingPath("$.couponCode").isEqualTo(COUPON);
            assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(userId.toString());
            assertThat(result).bodyJson().extractingPath("$.usedAt").isEqualTo(clock.instant().toString());
            assertThat(usedCount(COUPON)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM used_coupons_by_user WHERE user_id = ?", Integer.class, userId))
                    .isEqualTo(1);
            assertThat(reservationStore.findActiveExpiry(couponId(), userId, clock.instant())).isEmpty();
        }

        @Test
        @DisplayName("returns the original usage with 200 when the same user retries")
        void returnsOriginalUsageOnRetry() {
            reserve(COUPON, userId, POLISH_IP);
            useCoupon(COUPON, userId);
            Instant originalUsedAt = clock.instant();
            clock.advance(Duration.ofMinutes(1));

            MvcTestResult result = useCoupon(COUPON, userId);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.usedAt").isEqualTo(originalUsedAt.toString());
            assertThat(usedCount(COUPON)).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 404 when the user has no reservation")
        void rejectsUsageWithoutReservation() {
            MvcTestResult result = useCoupon(COUPON, userId);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("RESERVATION_NOT_FOUND");
            assertThat(usedCount(COUPON)).isZero();
        }

        @Test
        @DisplayName("returns 404 when the reservation has expired")
        void rejectsUsageOfExpiredReservation() {
            reserve(COUPON, userId, POLISH_IP);
            clock.advance(Duration.ofMinutes(16));

            MvcTestResult result = useCoupon(COUPON, userId);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("may have expired");
            assertThat(usedCount(COUPON)).isZero();
        }

        @Test
        @DisplayName("returns 400 for a malformed user id")
        void rejectsMalformedUserId() {
            MvcTestResult result = postJson("/api/v1/coupon-usages", """
                    {"couponCode": "SUMMER", "userId": "not-a-uuid"}
                    """);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("rejects a request without a coupon code")
        void rejectsMissingCouponCode() {
            MvcTestResult result = postJson("/api/v1/coupon-usages", """
                    {"userId": "%s"}
                    """.formatted(userId));

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.fieldErrors.couponCode").isNotNull();
        }
    }

    private void reserveAndUseCoupon(UUID user) {
        assertThat(reserve(COUPON, user, POLISH_IP)).hasStatus(HttpStatus.CREATED);
        assertThat(useCoupon(COUPON, user)).hasStatus(HttpStatus.CREATED);
    }

    private UUID couponId() {
        return couponId(COUPON);
    }

    private UUID couponId(String couponCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM coupon WHERE coupon_code = ?", UUID.class, couponCode);
    }
}

package com.example.empiktask.coupon.controller;

import static com.example.empiktask.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.empiktask.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@DisplayName("Coupon management flow")
class CouponControllerIntegrationTest extends IntegrationTest {

    private static final int PARALLEL_REQUESTS = 10;

    @BeforeEach
    void setUpCountries() {
        createCountry("PL", "Poland");
    }

    @Test
    @DisplayName("creates a coupon with zero usages and the creation date")
    void createsCoupon() {
        MvcTestResult result = createCoupon("Summer-2026", "pl", 10);

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/coupons/Summer-2026");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("Summer-2026");
        assertThat(result).bodyJson().extractingPath("$.countryCode").isEqualTo("PL");
        assertThat(result).bodyJson().extractingPath("$.maxUsages").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.usedCount").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-01-15T10:00:00Z");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT country_code, max_usages, used_count FROM coupon WHERE coupon_code = 'Summer-2026'");
        assertThat(row).containsEntry("country_code", "PL")
                .containsEntry("max_usages", 10)
                .containsEntry("used_count", 0);
    }

    @Test
    @DisplayName("rejects a coupon code that differs from an existing one only by case")
    void rejectsDuplicateCodeIgnoringCase() {
        createCoupon("SUMMER", "PL", 10);

        MvcTestResult result = createCoupon("summer", "PL", 5);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_CODE_ALREADY_EXISTS");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM coupon", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("returns COUPON_CODE_ALREADY_EXISTS to every loser when codes differing only by case are created in parallel")
    void parallelDuplicateCreatesReturnSpecificConflict() throws Exception {
        List<MvcTestResult> results = runConcurrently(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<MvcTestResult>>mapToObj(i -> () -> createCoupon(i % 2 == 0 ? "RACE" : "race", "PL", 5))
                .toList());

        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == HttpStatus.CREATED.value())
                .hasSize(1);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() != HttpStatus.CREATED.value())
                .hasSize(PARALLEL_REQUESTS - 1)
                .allSatisfy(result -> {
                    assertThat(result).hasStatus(HttpStatus.CONFLICT)
                            .bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_CODE_ALREADY_EXISTS");
                    assertThat(result).bodyJson().extractingPath("$.detail").asString()
                            .matches("A coupon with code '(RACE|race)' already exists\\. .*");
                });
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM coupon", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("enforces case-insensitive uniqueness at the database level")
    void databaseRejectsDuplicateCodeIgnoringCase() {
        createCoupon("WINTER", "PL", 10);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO coupon (id, coupon_code, country_code, created_at, max_usages, used_count) "
                                + "VALUES (gen_random_uuid(), 'winter', 'PL', now(), 1, 0)"))
                .hasMessageContaining("ux_coupon_code_case_insensitive");
    }

    @Test
    @DisplayName("rejects a coupon for a country that is not available")
    void rejectsUnknownCountry() {
        MvcTestResult result = createCoupon("SUMMER", "FR", 10);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUNTRY_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Country 'FR' is not available.");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "code with spaces      | {\"code\": \"SUM MER\", \"countryCode\": \"PL\", \"maxUsages\": 1} | code",
            "blank code            | {\"code\": \"\", \"countryCode\": \"PL\", \"maxUsages\": 1}        | code",
            "missing country       | {\"code\": \"SUMMER\", \"maxUsages\": 1}                         | countryCode",
            "invalid country code  | {\"code\": \"SUMMER\", \"countryCode\": \"POL\", \"maxUsages\": 1} | countryCode",
            "zero max usages       | {\"code\": \"SUMMER\", \"countryCode\": \"PL\", \"maxUsages\": 0}  | maxUsages",
            "missing max usages    | {\"code\": \"SUMMER\", \"countryCode\": \"PL\"}                   | maxUsages"
    })
    @DisplayName("rejects invalid coupon payloads with field details")
    void rejectsInvalidPayload(String scenario, String json, String invalidField) {
        MvcTestResult result = postJson("/api/v1/coupons", json);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors." + invalidField).isNotNull();
    }

    @Test
    @DisplayName("finds a coupon by code, ignoring case")
    void findsCouponIgnoringCase() {
        createCoupon("Summer", "PL", 3);

        MvcTestResult result = mvc.get().uri("/api/v1/coupons/{code}", "SUMMER").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("Summer");
        assertThat(result).bodyJson().extractingPath("$.maxUsages").isEqualTo(3);
    }

    @Test
    @DisplayName("returns 404 for an unknown coupon")
    void returnsNotFoundForUnknownCoupon() {
        MvcTestResult result = mvc.get().uri("/api/v1/coupons/{code}", "NOPE").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_NOT_FOUND");
    }
}

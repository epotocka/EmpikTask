package com.example.empiktask.country.controller;

import static com.example.empiktask.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.empiktask.support.IntegrationTest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@DisplayName("Country management flow")
class CountryControllerIntegrationTest extends IntegrationTest {

    private static final String COUNTRIES_URI = "/api/v1/countries";
    private static final int PARALLEL_REQUESTS = 10;

    @Test
    @DisplayName("creates a country and stores its code in upper case")
    void createsCountry() {
        MvcTestResult result = postJson(COUNTRIES_URI, """
                {"code": "pl", "name": "Poland"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/countries/PL");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PL");
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Poland");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM country WHERE code = 'PL'", String.class))
                .isEqualTo("Poland");
    }

    @Test
    @DisplayName("rejects a country whose code already exists, ignoring case")
    void rejectsDuplicateCountry() {
        createCountry("PL", "Poland");

        MvcTestResult result = postJson(COUNTRIES_URI, """
                {"code": "pl", "name": "Polska"}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUNTRY_ALREADY_EXISTS");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Country 'PL' already exists.");
    }

    @Test
    @DisplayName("returns COUNTRY_ALREADY_EXISTS to every loser when the same country is created in parallel")
    void parallelDuplicateCreatesReturnSpecificConflict() throws Exception {
        List<MvcTestResult> results = runConcurrently(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<MvcTestResult>>mapToObj(i -> () -> postJson(COUNTRIES_URI, """
                        {"code": "PL", "name": "Poland"}
                        """))
                .toList());

        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == HttpStatus.CREATED.value())
                .hasSize(1);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() != HttpStatus.CREATED.value())
                .hasSize(PARALLEL_REQUESTS - 1)
                .allSatisfy(result -> {
                    assertThat(result).hasStatus(HttpStatus.CONFLICT)
                            .bodyJson().extractingPath("$.errorCode").isEqualTo("COUNTRY_ALREADY_EXISTS");
                    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Country 'PL' already exists.");
                });
    }

    @ParameterizedTest(name = "[{index}] code={0}, name={1} -> invalid {2}")
    @CsvSource(nullValues = "null", value = {
            "POL, Poland, code",
            "P1, Poland, code",
            "null, Poland, code",
            "PL, '', name",
            "PL, null, name"
    })
    @DisplayName("rejects invalid country payloads with field details")
    void rejectsInvalidPayload(String code, String name, String invalidField) {
        String json = "{\"code\": %s, \"name\": %s}".formatted(jsonValue(code), jsonValue(name));

        MvcTestResult result = postJson(COUNTRIES_URI, json);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors." + invalidField).isNotNull();
    }

    @Test
    @DisplayName("lists countries sorted by code")
    void listsCountries() {
        createCountry("PL", "Poland");
        createCountry("DE", "Germany");

        MvcTestResult result = mvc.get().uri(COUNTRIES_URI).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].code").asArray().containsExactly("DE", "PL");
    }

    @Test
    @DisplayName("returns a single country by code, ignoring case")
    void findsCountryByCode() {
        createCountry("DE", "Germany");

        MvcTestResult result = mvc.get().uri(COUNTRIES_URI + "/de").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Germany");
    }

    @Test
    @DisplayName("returns 404 for an unknown country")
    void returnsNotFoundForUnknownCountry() {
        MvcTestResult result = mvc.get().uri(COUNTRIES_URI + "/FR").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUNTRY_NOT_FOUND");
    }

    private static String jsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}

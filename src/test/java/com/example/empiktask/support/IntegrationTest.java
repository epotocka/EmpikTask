package com.example.empiktask.support;

import static org.mockito.BDDMockito.given;

import com.example.empiktask.geolocation.client.GeolocationClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String POLISH_IP = "83.0.0.1";
    protected static final String GERMAN_IP = "85.0.0.1";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected MutableClock clock;

    @MockitoBean
    protected GeolocationClient geolocationClient;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE used_coupons_by_user, coupon, country CASCADE");
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        clock.reset();
        given(geolocationClient.countryCodeOf(POLISH_IP)).willReturn("PL");
        given(geolocationClient.countryCodeOf(GERMAN_IP)).willReturn("DE");
    }

    protected MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected void createCountry(String code, String name) {
        jdbcTemplate.update("INSERT INTO country (code, name) VALUES (?, ?)", code, name);
    }

    protected MvcTestResult createCoupon(String code, String countryCode, int maxUsages) {
        return postJson("/api/v1/coupons", """
                {"code": "%s", "countryCode": "%s", "maxUsages": %d}
                """.formatted(code, countryCode, maxUsages));
    }

    protected MvcTestResult reserve(String couponCode, UUID userId, String clientIp) {
        return mvc.post()
                .uri("/api/v1/reservations")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponUserJson(couponCode, userId))
                .exchange();
    }

    protected MvcTestResult useCoupon(String couponCode, UUID userId) {
        return postJson("/api/v1/coupon-usages", couponUserJson(couponCode, userId));
    }

    private static String couponUserJson(String couponCode, UUID userId) {
        return """
                {"couponCode": "%s", "userId": "%s"}
                """.formatted(couponCode, userId);
    }

    protected int usedCount(String couponCode) {
        Integer usedCount = jdbcTemplate.queryForObject(
                "SELECT used_count FROM coupon WHERE upper(coupon_code) = upper(?)", Integer.class, couponCode);
        return usedCount == null ? 0 : usedCount;
    }
}

package com.example.empiktask.geolocation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.empiktask.config.CouponProperties;
import com.example.empiktask.geolocation.exception.CountryNotResolvableException;
import com.example.empiktask.geolocation.exception.GeolocationUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(components = IpApiGeolocationClient.class,
        properties = {"coupons.geolocation.base-url=http://geo.test", "coupons.reservation.ttl=15m"})
@EnableConfigurationProperties(CouponProperties.class)
@DisplayName("ip-api.com geolocation client")
class IpApiGeolocationClientTest {

    private static final String LOOKUP_URL = "http://geo.test/json/83.0.0.1?fields=status,message,countryCode";

    @Autowired
    private IpApiGeolocationClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    @DisplayName("returns the upper-case country code for a successful lookup")
    void returnsCountryCode() {
        server.expect(requestTo(LOOKUP_URL))
                .andRespond(withSuccess("{\"status\":\"success\",\"countryCode\":\"pl\"}", MediaType.APPLICATION_JSON));

        assertThat(client.countryCodeOf("83.0.0.1")).isEqualTo("PL");
    }

    @Test
    @DisplayName("fails with a not-resolvable error when the service cannot locate the IP")
    void failsForUnresolvableIp() {
        server.expect(requestTo(LOOKUP_URL))
                .andRespond(withSuccess("{\"status\":\"fail\",\"message\":\"private range\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.countryCodeOf("83.0.0.1"))
                .isInstanceOf(CountryNotResolvableException.class)
                .hasMessageContaining("83.0.0.1");
    }

    @Test
    @DisplayName("fails with an unavailable error when the service is down")
    void failsWhenServiceIsDown() {
        server.expect(requestTo(LOOKUP_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.countryCodeOf("83.0.0.1"))
                .isInstanceOf(GeolocationUnavailableException.class);
    }
}

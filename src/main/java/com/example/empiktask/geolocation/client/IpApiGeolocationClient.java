package com.example.empiktask.geolocation.client;

import com.example.empiktask.config.CouponProperties;
import com.example.empiktask.geolocation.exception.CountryNotResolvableException;
import com.example.empiktask.geolocation.exception.GeolocationUnavailableException;
import com.example.empiktask.util.CountryCodesUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class IpApiGeolocationClient implements GeolocationClient {

    private static final String SUCCESS_STATUS = "success";

    private final RestClient restClient;

    IpApiGeolocationClient(RestClient.Builder restClientBuilder, CouponProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.geolocation().baseUrl()).build();
    }

    @Override
    public String countryCodeOf(String ipAddress) {
        IpApiResponse response = fetch(ipAddress);
        if (response == null || !SUCCESS_STATUS.equals(response.status()) || response.countryCode() == null
                || response.countryCode().isBlank()) {
            throw new CountryNotResolvableException(ipAddress);
        }
        return CountryCodesUtil.normalize(response.countryCode());
    }

    private IpApiResponse fetch(String ipAddress) {
        try {
            return restClient.get()
                    .uri("/json/{ip}?fields=status,message,countryCode", ipAddress)
                    .retrieve()
                    .body(IpApiResponse.class);
        } catch (RestClientException exception) {
            throw new GeolocationUnavailableException(exception);
        }
    }

    record IpApiResponse(String status, String message, String countryCode) {
    }
}

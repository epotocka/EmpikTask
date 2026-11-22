package com.example.empiktask.country.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCountryRequest(
        @NotBlank @Pattern(regexp = COUNTRY_CODE_PATTERN, message = COUNTRY_CODE_MESSAGE) String code,
        @NotBlank @Size(max = 100) String name) {

    private static final String COUNTRY_CODE_PATTERN = "^[A-Za-z]{2}$";
    private static final String COUNTRY_CODE_MESSAGE = "must be a two-letter ISO 3166-1 alpha-2 country code, e.g. PL";
}

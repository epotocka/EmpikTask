package com.example.empiktask.country.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CountryNotFoundException extends BusinessException {

    public CountryNotFoundException(String countryCode) {
        super(ErrorCode.COUNTRY_NOT_FOUND, "Country '%s' is not available.".formatted(countryCode));
    }
}

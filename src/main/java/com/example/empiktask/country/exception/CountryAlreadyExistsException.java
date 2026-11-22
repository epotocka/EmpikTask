package com.example.empiktask.country.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class CountryAlreadyExistsException extends BusinessException {

    public CountryAlreadyExistsException(String countryCode) {
        super(ErrorCode.COUNTRY_ALREADY_EXISTS, "Country '%s' already exists.".formatted(countryCode));
    }
}

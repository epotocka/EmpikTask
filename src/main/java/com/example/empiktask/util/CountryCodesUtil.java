package com.example.empiktask.util;

import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CountryCodesUtil {

    public String normalize(String countryCode) {
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }
}

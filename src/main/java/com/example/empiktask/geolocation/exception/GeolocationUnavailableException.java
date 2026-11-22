package com.example.empiktask.geolocation.exception;

import com.example.empiktask.error.BusinessException;
import com.example.empiktask.error.ErrorCode;

public class GeolocationUnavailableException extends BusinessException {

    public GeolocationUnavailableException(Throwable cause) {
        super(ErrorCode.GEOLOCATION_UNAVAILABLE,
                "We could not verify your country right now. Please try again in a moment.", cause);
    }
}

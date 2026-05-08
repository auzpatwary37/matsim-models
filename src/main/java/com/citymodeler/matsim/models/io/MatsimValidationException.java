package com.citymodeler.matsim.models.io;

public class MatsimValidationException extends MatsimModelException {
    public MatsimValidationException(String message) {
        super(message);
    }

    public MatsimValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

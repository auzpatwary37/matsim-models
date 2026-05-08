package com.citymodeler.matsim.models.io;

public class MatsimParseException extends MatsimModelException {
    public MatsimParseException(String message) {
        super(message);
    }

    public MatsimParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.citymodeler.matsim.models.io;

public class MatsimWriteException extends MatsimModelException {
    public MatsimWriteException(String message) {
        super(message);
    }

    public MatsimWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.shubham.featureflagplatform.error;

public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(Long id) {
        super("Flag not found with id: " + id);
    }
}

package com.gymkhata.common.error;

public class DuplicateKeyException extends RuntimeException {

    public DuplicateKeyException(String message) {
        super(message);
    }
}

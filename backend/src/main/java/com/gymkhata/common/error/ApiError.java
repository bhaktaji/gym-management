package com.gymkhata.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(int status, String code, String message, Instant timestamp, Map<String, Object> details) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, Instant.now(), null);
    }

    public static ApiError of(int status, String code, String message, Map<String, Object> details) {
        return new ApiError(status, code, message, Instant.now(), details);
    }
}

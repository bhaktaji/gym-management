package com.gymkhata.common.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST.value(), code, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND.value(), code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT.value(), code, message);
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN.value(), code, message);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

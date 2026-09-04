package com.company.prototype.worker.packagecheck;

import com.company.prototype.common.error.ApiErrorCode;

public class PackageValidationException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public PackageValidationException(ApiErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public PackageValidationException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public ApiErrorCode getCode() {
        return errorCode;
    }
}

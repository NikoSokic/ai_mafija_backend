package com.niko.ai_mafija_backend.exception;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode code, String message) {
        super(code, message);
    }
}

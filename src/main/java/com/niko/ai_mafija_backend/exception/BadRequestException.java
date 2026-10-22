package com.niko.ai_mafija_backend.exception;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode code, String message) {
        super(code, message);
    }
}

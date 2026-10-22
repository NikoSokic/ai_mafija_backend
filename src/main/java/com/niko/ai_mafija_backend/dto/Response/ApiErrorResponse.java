package com.niko.ai_mafija_backend.dto.Response;

import com.niko.ai_mafija_backend.exception.ErrorCode;

import java.time.OffsetDateTime;

/** `code` je stabilan (vidi ErrorCode, u JSON-u ime koda); `message` je tekst za ljude i smije se mijenjati. */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
) {
}

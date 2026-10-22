package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

public record AvailableActionResponse(
        String type,
        List<Long> targetPlayerIds,
        boolean requiresSecondTarget
) {
}

package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HostActionRequest(
        @NotNull(message = "ID igrača je obavezan.") Long playerId,
        @NotBlank(message = "Player token je obavezan.") String playerToken
) {
}

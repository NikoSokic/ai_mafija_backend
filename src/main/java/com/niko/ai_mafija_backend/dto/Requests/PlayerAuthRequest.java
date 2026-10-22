package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Tko je igrač i njegov token – npr. za napuštanje sobe */
public record PlayerAuthRequest(
        @NotNull(message = "ID igrača je obavezan.") Long playerId,
        @NotBlank(message = "Player token je obavezan.") String playerToken
) {
}

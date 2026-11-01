package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Domaćin izbacuje igrača iz čekaonice */
public record KickPlayerRequest(
        @NotNull(message = "ID igrača je obavezan.") Long playerId,
        @NotBlank(message = "Player token je obavezan.") String playerToken,
        @NotNull(message = "ID izbačenog igrača je obavezan.") Long targetPlayerId
) {
}

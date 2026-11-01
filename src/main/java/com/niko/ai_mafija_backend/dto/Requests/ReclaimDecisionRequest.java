package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Domaćin odobrava ili odbija zahtjev za povratak */
public record ReclaimDecisionRequest(
        @NotNull(message = "ID igrača je obavezan.") Long playerId,
        @NotBlank(message = "Player token je obavezan.") String playerToken,
        @NotNull(message = "Odluka je obavezna.") Boolean approve
) {
}

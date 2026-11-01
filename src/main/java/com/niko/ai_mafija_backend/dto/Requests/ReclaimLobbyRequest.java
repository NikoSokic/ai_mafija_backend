package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Igrač na novom uređaju traži povratak na svoje mjesto u sobi (domaćin odobrava) */
public record ReclaimLobbyRequest(
        @NotBlank(message = "Nickname je obavezan.")
        @Size(max = 16, message = "Nickname može imati najviše 16 znakova.") String nickname
) {
}

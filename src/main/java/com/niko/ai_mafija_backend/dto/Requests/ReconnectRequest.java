package com.niko.ai_mafija_backend.dto.Requests;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
public class ReconnectRequest {

    @NotNull(message = "ID igrača je obavezan.")
    private Long playerId;

    @NotBlank(message = "Player token je obavezan.")
    private String playerToken;
}

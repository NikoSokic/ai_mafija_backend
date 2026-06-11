package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class JoinLobbyRequest {

    @NotBlank(message = "Nickname je obavezan.")
    private String nickname;

}

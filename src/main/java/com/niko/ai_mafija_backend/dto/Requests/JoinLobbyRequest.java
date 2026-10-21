package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class JoinLobbyRequest {

    @NotBlank(message = "Nickname je obavezan.")
    @Size(max = 16, message = "Nickname može imati najviše 16 znakova.")
    private String nickname;

}

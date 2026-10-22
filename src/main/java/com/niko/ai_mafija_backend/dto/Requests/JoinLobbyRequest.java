package com.niko.ai_mafija_backend.dto.Requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class JoinLobbyRequest {

    @NotBlank(message = "Nickname je obavezan.")
    @Size(max = 16, message = "Nickname može imati najviše 16 znakova.")
    @Schema(description = "Nadimak, jedinstven u sobi (bez obzira na velika/mala slova)", example = "Ana", maxLength = 16)
    private String nickname;

    @Size(max = 8, message = "Avatar može imati najviše 8 znakova.")
    @Schema(description = "Emoji avatar (neobavezno)", example = "🦊", maxLength = 8, nullable = true)
    private String avatarEmoji;

}

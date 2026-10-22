package com.niko.ai_mafija_backend.dto.Requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateLobbyRequest {

    @NotBlank(message = "Nickname hosta je obavezan.")
    @Size(max = 16, message = "Nickname hosta može imati najviše 16 znakova.")
    @Schema(description = "Nadimak domaćina", example = "Niko", maxLength = 16)
    private String hostNickname;

    @NotNull(message = "Maksimalan broj igrača je obavezan.")
    @Min(value = 4, message = "Lobby mora imati najmanje 4 igrača.")
    @Max(value = 20, message = "Lobby može imati najviše 20 igrača.")
    private Integer maxPlayers;

    @NotEmpty(message = "Potrebno je odabrati barem jednu ulogu.")
    @Valid
    private List<LobbyRoleRequest> roles;

    @Pattern(regexp = "LIVE|ONLINE", message = "Način igre mora biti LIVE ili ONLINE.")
    @Schema(description = "Način igre (neobavezno)", allowableValues = {"LIVE", "ONLINE"}, defaultValue = "LIVE")
    private String mode;

    @Schema(description = "Javna soba se pojavljuje na popisu javnih soba (neobavezno)", defaultValue = "false")
    private Boolean isPublic;

    @Schema(description = "Glas naratora (neobavezno)", defaultValue = "true")
    private Boolean voiceEnabled;

    @Size(max = 8, message = "Avatar može imati najviše 8 znakova.")
    @Schema(description = "Emoji avatar domaćina (neobavezno)", example = "🦊", maxLength = 8, nullable = true)
    private String hostAvatarEmoji;
}

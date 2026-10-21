package com.niko.ai_mafija_backend.dto.Requests;

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
    private String hostNickname;

    @NotNull(message = "Maksimalan broj igrača je obavezan.")
    @Min(value = 4, message = "Lobby mora imati najmanje 4 igrača.")
    @Max(value = 20, message = "Lobby može imati najviše 20 igrača.")
    private Integer maxPlayers;

    @NotEmpty(message = "Potrebno je odabrati barem jednu ulogu.")
    @Valid
    private List<LobbyRoleRequest> roles;
}

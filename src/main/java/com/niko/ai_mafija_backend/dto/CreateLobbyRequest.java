package com.niko.ai_mafija_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateLobbyRequest {

    private String hostNickname;

    @NotNull
    @Min(4)
    @Max(20)
    private Integer maxPlayers;

    @NotEmpty
    @Valid
    private List<LobbyRoleRequest> roles;
}
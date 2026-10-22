package com.niko.ai_mafija_backend.dto.Response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class LobbyDetailsResponse {

    private Long id;
    private String code;
    private String status;
    private Integer maxPlayers;

    private String phase;
    private Integer roundNumber;

    private List<LobbyPlayerResponse> players;

    /** Sastav uloga koji je domaćin odabrao */
    private List<LobbyRoleResponse> roles;

    @Schema(description = "ID igrača koji je trenutno domaćin; null ako domaćina nema")
    private Long hostPlayerId;

    @Schema(description = "Način igre", allowableValues = {"LIVE", "ONLINE"}, example = "LIVE")
    private String mode;

    @Schema(description = "Javna soba se pojavljuje na popisu javnih soba", example = "false")
    private Boolean isPublic;

    @Schema(description = "Glas naratora", example = "true")
    private Boolean voiceEnabled;
}

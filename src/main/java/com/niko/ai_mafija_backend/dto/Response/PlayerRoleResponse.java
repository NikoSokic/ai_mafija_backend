package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class PlayerRoleResponse {

    private Long playerId;
    private String nickname;
    private String roleName;

    /** Suigrači za tim mafije (Mafia, Dama); prazno za građane */
    private List<TeammateResponse> teammates;
}

package com.niko.ai_mafija_backend.dto;

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
}
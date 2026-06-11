package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LobbyPlayerResponse {

    private Long id;
    private String nickname;
    private Boolean alive;
}
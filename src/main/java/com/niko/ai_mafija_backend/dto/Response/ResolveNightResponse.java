package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ResolveNightResponse {

    private String message;
    private String phase;
    private Integer roundNumber;
    private List<Long> killedPlayerIds;
    private List<Long> silencedPlayerIds;
}
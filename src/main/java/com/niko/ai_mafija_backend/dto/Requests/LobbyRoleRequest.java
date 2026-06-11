package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyRoleRequest {

    @NotNull
    private Long roleId;

    @NotNull
    @Min(1)
    private Integer count;
}
package com.niko.ai_mafija_backend.dto.Requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyRoleRequest {

    @NotNull(message = "ID uloge je obavezan.")
    @Positive(message = "ID uloge mora biti pozitivan broj.")
    private Long roleId;

    @NotNull(message = "Broj uloga je obavezan.")
    @Min(value = 1, message = "Broj uloga mora biti veći od nule.")
    private Integer count;
}

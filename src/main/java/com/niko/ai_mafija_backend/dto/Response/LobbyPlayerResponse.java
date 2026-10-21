package com.niko.ai_mafija_backend.dto.Response;

import com.niko.ai_mafija_backend.model.Player;
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
    private Boolean host;

    /** NIGHT, VOTE ili LEFT; null dok je igrač živ */
    private String eliminatedHow;
    private Integer eliminatedRound;

    /** Uloga je javna tek kad igra završi; prije toga null */
    private String roleName;

    public static LobbyPlayerResponse from(Player player, boolean revealRole) {
        return new LobbyPlayerResponse(
                player.getId(),
                player.getNickname(),
                player.getIsAlive(),
                Boolean.TRUE.equals(player.getIsHost()),
                player.getEliminatedHow(),
                player.getEliminatedRound(),
                revealRole && player.getRole() != null ? player.getRole().getName() : null
        );
    }
}

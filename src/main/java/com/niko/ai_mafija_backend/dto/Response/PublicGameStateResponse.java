package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Javno stanje igre – stiže u događaju GAME_STATE_CHANGED.
 *
 * @param voting          null dok glasanje nije otvoreno
 * @param currentTurnRole koja je uloga noću budna (MAFIA, DAMA, Doktor…); null u uvodu noći i danju.
 *                        Ne otkriva tko je živ: potez dobije svaka uloga iz sastava, mrtva glumljeni.
 * @param lastNight       rezultat zadnje noći tijekom dana (DAY, VOTING, TIE_BREAK); inače null
 * @param readyPlayerIds  tko je spreman u uvodu prve noći; izvan uvoda prve noći null
 */
public record PublicGameStateResponse(
        String lobbyCode,
        String lobbyStatus,
        String phase,
        Integer roundNumber,
        List<LobbyPlayerResponse> players,
        boolean gameFinished,
        String winner,
        boolean votingOpen,
        List<Long> tiedPlayerIds,
        OffsetDateTime phaseEndsAt,
        OffsetDateTime serverTime,
        List<Long> silencedPlayerIds,
        List<Long> nightKilledPlayerIds,
        PublicVotingResponse voting,
        String currentTurnRole,
        LastNightResponse lastNight,
        List<Long> readyPlayerIds
) {
}

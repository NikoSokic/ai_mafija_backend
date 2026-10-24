package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param phaseEndsAt          kraj rasprave / glasanja; tijekom noći null (rok poteza je u currentPlayer.actionEndsAt)
 * @param serverTime           vrijeme poslužitelja, da klijent može ispraviti razliku u satu
 * @param silencedPlayerIds    utišani u tekućem danu
 * @param nightKilledPlayerIds tko je stradao u noći prije tekućeg dana
 * @param currentTurnRole      koja je uloga noću budna (MAFIA, DAMA, Doktor…); null u uvodu noći i danju
 * @param lastNight            rezultat zadnje noći tijekom dana (DAY, VOTING, TIE_BREAK); inače null
 * @param readyPlayerIds       tko je spreman u uvodu prve noći; izvan uvoda prve noći null
 */
public record GameStateResponse(
        String lobbyCode,
        String lobbyStatus,
        String phase,
        Integer roundNumber,
        List<LobbyPlayerResponse> players,
        CurrentPlayerGameStateResponse currentPlayer,
        boolean gameFinished,
        String winner,
        VotingStateResponse voting,
        OffsetDateTime phaseEndsAt,
        OffsetDateTime serverTime,
        List<Long> silencedPlayerIds,
        List<Long> nightKilledPlayerIds,
        String currentTurnRole,
        LastNightResponse lastNight,
        List<Long> readyPlayerIds
) {
}

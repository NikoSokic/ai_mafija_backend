package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param actionEndsAt rok poteza; samo dok je igrač na redu
 * @param teammates    samo za tim mafije
 * @param teamTargets  samo za tim mafije tijekom noći: tko je koga odabrao za KILL
 * @param eliminatedHow NIGHT, VOTE ili LEFT; null dok je igrač živ
 */
public record CurrentPlayerGameStateResponse(
        Long playerId,
        String nickname,
        boolean alive,
        String role,
        boolean canAct,
        boolean hasActed,
        List<AvailableActionResponse> availableActions,
        OffsetDateTime actionEndsAt,
        List<TeammateResponse> teammates,
        List<TeamTargetResponse> teamTargets,
        String eliminatedHow,
        Integer eliminatedRound
) {
}

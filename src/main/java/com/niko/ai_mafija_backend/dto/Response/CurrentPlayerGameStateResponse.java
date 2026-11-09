package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param actionEndsAt    rok odabira (u drugom krugu mafije rok drugog kruga); samo dok je igrač na redu.
 *                        Potez može završiti i ranije: kratko nakon što svi na redu odigraju.
 * @param teammates       samo za tim mafije
 * @param teamTargets     samo za tim mafije tijekom noći: tko je koga odabrao za KILL
 * @param eliminatedHow   NIGHT, VOTE ili LEFT; null dok je igrač živ
 * @param actionStartedAt kraj naracije, od kada igrač bira (za traku vremena); samo dok je igrač na redu
 * @param mafiaRunoff     drugi krug mafije, samo za tim mafije dok traje; inače null
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
        Integer eliminatedRound,
        OffsetDateTime actionStartedAt,
        MafiaRunoffResponse mafiaRunoff
) {
}

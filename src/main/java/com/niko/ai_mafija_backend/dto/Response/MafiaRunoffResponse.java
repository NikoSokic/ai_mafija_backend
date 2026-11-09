package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Drugi krug mafije (samo za tim mafije): mafijaši su odabrali različite mete, pa sada biraju samo između njih.
 * Ako se do endsAt ne slože, server nasumično odabere jednog od kandidata.
 *
 * @param candidatePlayerIds mete iz prvog kruga; KILL u availableActions nudi samo njih
 */
public record MafiaRunoffResponse(
        boolean active,
        List<Long> candidatePlayerIds,
        OffsetDateTime endsAt
) {
}

package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

/**
 * Odgovor na javljanje igrača: trenutni domaćin, igrači koji se ne javljaju ("Nije tu") i zahtjevi za povratak
 * na novom uređaju koji čekaju odluku (samo domaćinu, inače prazno)
 */
public record HeartbeatResponse(Long hostPlayerId, List<Long> awayPlayerIds,
                                List<ReclaimPendingResponse> reclaimRequests) {
}

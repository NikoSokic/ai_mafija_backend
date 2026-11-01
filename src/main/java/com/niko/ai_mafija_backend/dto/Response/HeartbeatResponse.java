package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

/** Odgovor na javljanje igrača: trenutni domaćin i igrači koji se ne javljaju ("Nije tu") */
public record HeartbeatResponse(Long hostPlayerId, List<Long> awayPlayerIds) {
}

package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

/** Rezultat zadnje noći; javan tijekom dana (rasprava, glasanje, ponovno glasanje) te runde */
public record LastNightResponse(Integer roundNumber, List<Long> killedPlayerIds, List<Long> silencedPlayerIds) {
}

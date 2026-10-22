package com.niko.ai_mafija_backend.dto.Response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Redak popisa javnih soba (GET /api/lobbies?public=true) */
public record PublicLobbyResponse(
        String code,
        String hostNickname,
        @Schema(description = "Emoji avatar domaćina; null ako ga nije odabrao", nullable = true) String hostAvatarEmoji,
        @Schema(description = "Koliko je igrača trenutno u sobi") long playerCount,
        Integer maxPlayers,
        @Schema(allowableValues = {"LIVE", "ONLINE"}) String mode,
        OffsetDateTime createdAt
) {
}

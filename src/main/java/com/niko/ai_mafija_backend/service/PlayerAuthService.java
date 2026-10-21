package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.exception.UnauthorizedException;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlayerAuthService {

    private final PlayerRepository playerRepository;

    public Player authenticate(Long playerId, String playerToken) {
        if (playerId == null) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "ID igrača je obavezan.");
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND, "Igrač ne postoji."));

        if (playerToken == null || playerToken.isBlank()) {
            throw new UnauthorizedException(ErrorCode.TOKEN_REQUIRED, "Player token je obavezan.");
        }

        // igrač koji je napustio sobu više nema token
        if (!playerToken.equals(player.getPlayerToken())) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Neispravan player token.");
        }

        return player;
    }

    public Player authenticateHostForLobby(Long playerId, String playerToken, Lobby lobby) {
        Player player = authenticate(playerId, playerToken);

        if (!player.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač ne pripada ovom lobbyju.");
        }

        if (!Boolean.TRUE.equals(player.getIsHost())) {
            throw new ForbiddenException(ErrorCode.NOT_HOST, "Samo host može izvršiti ovu operaciju.");
        }

        return player;
    }
}

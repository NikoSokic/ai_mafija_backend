package com.niko.ai_mafija_backend.service;

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
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Igrač ne postoji."));

        if (playerToken == null || playerToken.isBlank()) {
            throw new UnauthorizedException("Player token je obavezan.");
        }

        if (!playerToken.equals(player.getPlayerToken())) {
            throw new UnauthorizedException("Neispravan player token.");
        }

        return player;
    }

    public Player authenticateHostForLobby(Long playerId, String playerToken, Lobby lobby) {
        Player player = authenticate(playerId, playerToken);

        if (!player.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException("Igrač ne pripada ovom lobbyju.");
        }

        if (!Boolean.TRUE.equals(player.getIsHost())) {
            throw new ForbiddenException("Samo host može izvršiti ovu operaciju.");
        }

        return player;
    }
}

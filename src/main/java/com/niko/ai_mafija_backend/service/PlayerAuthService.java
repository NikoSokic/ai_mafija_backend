package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlayerAuthService {

    private final PlayerRepository playerRepository;

    public Player authenticate(Long playerId, String playerToken) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Igrac ne postoji."));

        if (playerToken == null || playerToken.isBlank()) {
            throw new RuntimeException("Player token je obavezan.");
        }

        if (!playerToken.equals(player.getPlayerToken())) {
            throw new RuntimeException("Neispravan player token.");
        }

        return player;
    }
}
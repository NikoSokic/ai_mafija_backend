package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {

    boolean existsByPlayerAndPhaseAndRoundNumber(
            Player player,
            String phase,
            Integer roundNumber
    );
    List<GameAction> findByLobbyAndPhaseAndRoundNumber(
            Lobby lobby,
            String phase,
            Integer roundNumber
    );

}
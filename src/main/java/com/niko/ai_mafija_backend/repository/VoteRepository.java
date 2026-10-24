package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    boolean existsByLobbyAndVoterPlayerAndRoundNumber(
            Lobby lobby,
            Player voterPlayer,
            Integer roundNumber
    );

    List<Vote> findByLobbyAndRoundNumber(
            Lobby lobby,
            Integer roundNumber
    );

    boolean existsByLobbyAndVoterPlayerAndRoundNumberAndPhase(
            Lobby lobby,
            Player voterPlayer,
            Integer roundNumber,
            String phase
    );
    List<Vote> findByLobbyAndRoundNumberAndPhase(
            Lobby lobby,
            Integer roundNumber,
            String phase
    );

    /** Svi glasovi sobe – za novu igru u istoj sobi */
    void deleteByLobby(Lobby lobby);
}
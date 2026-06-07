package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    long countByLobby(Lobby lobby);
    List<Player> findByLobby(Lobby lobby);
    boolean existsByLobbyAndNicknameIgnoreCase(Lobby lobby, String nickname);
}
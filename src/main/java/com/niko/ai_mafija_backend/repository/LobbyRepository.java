package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LobbyRepository extends JpaRepository<Lobby,Long> {
}

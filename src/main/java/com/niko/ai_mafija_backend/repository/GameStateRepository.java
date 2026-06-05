package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.GameState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateRepository extends JpaRepository<GameState,Long> {
}

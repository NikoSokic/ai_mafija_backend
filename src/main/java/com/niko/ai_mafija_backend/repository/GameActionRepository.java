package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameActionRepository extends JpaRepository<GameAction,Long> {
}

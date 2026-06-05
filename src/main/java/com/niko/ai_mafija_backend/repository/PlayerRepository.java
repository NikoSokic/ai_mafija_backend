package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player,Long> {
}

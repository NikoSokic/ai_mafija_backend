package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LobbyRepository extends JpaRepository<Lobby,Long> {
    Optional<Lobby> findByCode(String code);

    /** Javne sobe za popis: samo sa zadanim statusom, najnovije prve, najviše 20 */
    List<Lobby> findTop20ByStatusAndIsPublicTrueOrderByCreatedAtDesc(String status);
}

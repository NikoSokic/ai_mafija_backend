package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GameStateRepository extends JpaRepository<GameState,Long> {

    Optional<GameState> findByLobby(Lobby lobby);

    /**
     * Zaključa red stanja igre do kraja transakcije (SELECT ... FOR UPDATE). Sve što mijenja igru
     * (akcija, glas, istek roka, gumbi domaćina) ide preko ovoga, pa se ne mogu preklopiti.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameState g where g.lobby = :lobby")
    Optional<GameState> findByLobbyForUpdate(@Param("lobby") Lobby lobby);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameState g where g.id = :id")
    Optional<GameState> findByIdForUpdate(@Param("id") Long id);

    List<GameState> findByPhaseEndsAtBefore(OffsetDateTime time);
}

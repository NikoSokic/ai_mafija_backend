package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LobbyRepository extends JpaRepository<Lobby,Long> {
    Optional<Lobby> findByCode(String code);

    /**
     * Zaključa red sobe do kraja transakcije. Ulazak, izlazak i pokretanje idu preko ovoga,
     * pa istodobni ulasci ne mogu prepuniti sobu ni zauzeti isti nadimak.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lobby l where l.code = :code")
    Optional<Lobby> findByCodeForUpdate(@Param("code") String code);

    /** Javne sobe za popis: samo sa zadanim statusom, najnovije prve, najviše 20 */
    List<Lobby> findTop20ByStatusAndIsPublicTrueOrderByCreatedAtDesc(String status);
}

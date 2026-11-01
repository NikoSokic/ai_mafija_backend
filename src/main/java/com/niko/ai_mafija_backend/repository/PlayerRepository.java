package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    long countByLobby(Lobby lobby);
    List<Player> findByLobby(Lobby lobby);
    boolean existsByLobbyAndNicknameIgnoreCase(Lobby lobby, String nickname);
    List<Player> findByLobbyAndIsAliveTrue(Lobby lobby);
    Optional<Player> findByLobbyAndNicknameIgnoreCase(Lobby lobby, String nickname);

    /** Je li se itko iz sobe javio od zadanog trenutka */
    @Query("select count(p) > 0 from Player p where p.lobby = :lobby and coalesce(p.lastSeenAt, p.joinedAt) >= :since")
    boolean existsActivitySince(@Param("lobby") Lobby lobby, @Param("since") OffsetDateTime since);

    /**
     * Upisuje samo zadnje javljanje. Ne sprema cijelog igrača, da zastarjeli red ne prepiše
     * promjene koje je u međuvremenu napravila igra (živ, spreman…).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Player p set p.lastSeenAt = :seenAt where p.id = :id")
    void touch(@Param("id") Long id, @Param("seenAt") OffsetDateTime seenAt);
}
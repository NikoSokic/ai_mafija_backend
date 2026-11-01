package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.ReclaimRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReclaimRequestRepository extends JpaRepository<ReclaimRequest, Long> {

    Optional<ReclaimRequest> findByIdAndLobbyId(Long id, Long lobbyId);

    List<ReclaimRequest> findByLobbyIdAndStatusAndExpiresAtAfter(Long lobbyId, String status, OffsetDateTime now);

    List<ReclaimRequest> findByPlayerIdAndStatus(Long playerId, String status);

    void deleteByExpiresAtBefore(OffsetDateTime time);
}

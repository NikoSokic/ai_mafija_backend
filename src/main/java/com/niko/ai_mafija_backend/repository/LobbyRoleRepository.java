package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.LobbyRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LobbyRoleRepository extends JpaRepository<LobbyRole,Long> {

    List<LobbyRole> findByLobby(Lobby lobby);

}

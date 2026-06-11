package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Requests.CreateLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Response.CreateLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.JoinLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyDetailsResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.service.LobbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.niko.ai_mafija_backend.service.PlayerService;

import java.util.List;

@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor

public class LobbyController {

    private final LobbyService lobbyService;
    private final PlayerService playerService;

    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(@RequestBody CreateLobbyRequest request) {
        return ResponseEntity.ok(lobbyService.createLobby(request));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<JoinLobbyResponse> joinLobby(
            @PathVariable String code,
            @Valid @RequestBody JoinLobbyRequest request
    ) {
        return ResponseEntity.ok(playerService.joinLobby(code, request));
    }

    @GetMapping("/{code}/players")
    public ResponseEntity<List<LobbyPlayerResponse>> getPlayers(@PathVariable String code) {
        return ResponseEntity.ok(playerService.getPlayersByLobby(code));
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<Void> startLobby(@PathVariable String code) {
        lobbyService.startLobby(code);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{code}")
    public ResponseEntity<LobbyDetailsResponse> getLobbyDetails(
            @PathVariable String code) {

        return ResponseEntity.ok(
                lobbyService.getLobbyDetails(code)
        );
    }

}

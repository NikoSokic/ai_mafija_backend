package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Requests.CreateLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Response.GameStateResponse;
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
import com.niko.ai_mafija_backend.service.GameStateService;
import com.niko.ai_mafija_backend.service.VoteService;

import java.util.List;

@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor

public class LobbyController {

    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final GameStateService gameStateService;
    private final VoteService voteService;

    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(@Valid @RequestBody CreateLobbyRequest request) {
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
    public ResponseEntity<Void> startLobby(
            @PathVariable String code,
            @Valid @RequestBody HostActionRequest request) {
        lobbyService.startLobby(code, request);
        return ResponseEntity.ok().build();
    }

    /** Samo domaćin: rasprava traje 30 s dulje, svima */
    @PostMapping("/{code}/discussion/extend")
    public ResponseEntity<Void> extendDiscussion(
            @PathVariable String code,
            @Valid @RequestBody HostActionRequest request) {
        voteService.extendDiscussionAsHost(code, request);
        return ResponseEntity.ok().build();
    }

    /** Samo domaćin: glasanje počinje odmah, svima */
    @PostMapping("/{code}/voting/start")
    public ResponseEntity<Void> startVoting(
            @PathVariable String code,
            @Valid @RequestBody HostActionRequest request) {
        voteService.startVotingAsHost(code, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<Void> leaveLobby(
            @PathVariable String code,
            @Valid @RequestBody PlayerAuthRequest request) {
        playerService.leaveLobby(code, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{code}/game-state")
    public ResponseEntity<GameStateResponse> getGameState(
            @PathVariable String code,
            @RequestHeader("X-Player-Id") Long playerId,
            @RequestHeader("X-Player-Token") String playerToken) {
        return ResponseEntity.ok(gameStateService.getGameState(code, playerId, playerToken));
    }

    @GetMapping("/{code}")
    public ResponseEntity<LobbyDetailsResponse> getLobbyDetails(
            @PathVariable String code) {

        return ResponseEntity.ok(
                lobbyService.getLobbyDetails(code)
        );
    }

}

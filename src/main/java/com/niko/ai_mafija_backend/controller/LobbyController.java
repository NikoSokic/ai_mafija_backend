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
import com.niko.ai_mafija_backend.dto.Response.PublicLobbyResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.service.LobbyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sobe", description = "Stvaranje sobe, ulazak, izlazak, popis javnih soba i stanje igre")
public class LobbyController {

    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final GameStateService gameStateService;
    private final VoteService voteService;

    @Operation(summary = "Stvori sobu",
            description = "Domaćin dobiva playerId i playerToken. Postavke mode (LIVE|ONLINE, zadano LIVE), "
                    + "isPublic (zadano false), voiceEnabled (zadano true) i hostAvatarEmoji su neobavezne. "
                    + "Greške: 400 VALIDATION_ERROR (npr. nadimak prazan ili duži od 16 znakova, nepoznat mode), "
                    + "400 INVALID_ROLE_CONFIG, 404 ROLE_NOT_FOUND.")
    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(@Valid @RequestBody CreateLobbyRequest request) {
        return ResponseEntity.ok(lobbyService.createLobby(request));
    }

    @Operation(summary = "Popis javnih soba",
            description = "Sobe sa statusom WAITING i isPublic=true, najnovije prve, najviše 20. "
                    + "Privatne sobe se ne izlistavaju: public=false vraća 400 VALIDATION_ERROR.")
    @GetMapping
    public ResponseEntity<List<PublicLobbyResponse>> getPublicLobbies(
            @Parameter(description = "Mora biti true") @RequestParam(name = "public", defaultValue = "true") boolean publicOnly) {
        if (!publicOnly) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Popis privatnih soba nije dostupan.");
        }
        return ResponseEntity.ok(lobbyService.getPublicLobbies());
    }

    @Operation(summary = "Uđi u sobu",
            description = "Nadimak je jedinstven u sobi (bez obzira na velika/mala slova); avatarEmoji je neobavezan. "
                    + "Greške: 400 VALIDATION_ERROR, 404 LOBBY_NOT_FOUND, 409 NICKNAME_TAKEN, LOBBY_FULL, GAME_ALREADY_STARTED.")
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

    @Operation(summary = "Napusti sobu",
            description = "Samo dok soba čeka igrače (WAITING): igrač se briše, nadimak se oslobađa, događaj PLAYER_LEFT. "
                    + "Ako izlazi domaćin, domaćin postaje igrač koji je najduže u sobi (događaj HOST_CHANGED {hostPlayerId}); "
                    + "ako nema nikoga, soba se briše. Tijekom igre vraća 409 GAME_IN_PROGRESS. "
                    + "Greške: 401 INVALID_TOKEN, 403 NOT_IN_LOBBY, 404 LOBBY_NOT_FOUND.")
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

    @Operation(summary = "Detalji sobe",
            description = "Javni podaci: igrači (host, avatarEmoji), hostPlayerId, sastav uloga (roles), "
                    + "postavke (mode, isPublic, voiceEnabled). Uloge igrača su tajne dok igra traje.")
    @GetMapping("/{code}")
    public ResponseEntity<LobbyDetailsResponse> getLobbyDetails(
            @PathVariable String code) {

        return ResponseEntity.ok(
                lobbyService.getLobbyDetails(code)
        );
    }

}

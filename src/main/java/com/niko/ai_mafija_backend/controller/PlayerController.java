package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReconnectRequest;
import com.niko.ai_mafija_backend.dto.Response.HeartbeatResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerRoleResponse;
import com.niko.ai_mafija_backend.dto.Response.ReconnectResponse;
import com.niko.ai_mafija_backend.service.HostPresenceService;
import com.niko.ai_mafija_backend.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final HostPresenceService hostPresenceService;

    @PostMapping("/role")
    public ResponseEntity<PlayerRoleResponse> getPlayerRole(@RequestBody PlayerRoleRequest request) {
        return ResponseEntity.ok(playerService.getPlayerRole(request));
    }

    @Operation(summary = "Javljanje igrača (heartbeat)",
            description = "Klijent poziva svakih ~15 s i pri povratku u aplikaciju. Backend zapisuje zadnje javljanje; "
                    + "odgovor: hostPlayerId i awayPlayerIds (igrači koji se ne javljaju 45 s, bez pozivatelja). "
                    + "Domaćin koji se ne javlja gubi ulogu: 120 s u čekaonici i na kraju igre, 45 s tijekom igre "
                    + "(događaj HOST_CHANGED); bivši domaćin ostaje običan igrač. "
                    + "Greške: 401 INVALID_TOKEN (npr. igrača je izbacio domaćin), 404 PLAYER_NOT_FOUND.")
    @PostMapping("/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(@Valid @RequestBody PlayerAuthRequest request) {
        return ResponseEntity.ok(hostPresenceService.heartbeat(request));
    }

    @PostMapping("/reconnect")
    public ResponseEntity<ReconnectResponse> reconnect(@Valid @RequestBody ReconnectRequest request) {
        return ResponseEntity.ok(playerService.reconnect(request));
    }
}

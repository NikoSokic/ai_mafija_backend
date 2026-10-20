package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Requests.PlayerRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReconnectRequest;
import com.niko.ai_mafija_backend.dto.Response.PlayerRoleResponse;
import com.niko.ai_mafija_backend.dto.Response.ReconnectResponse;
import com.niko.ai_mafija_backend.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping("/role")
    public ResponseEntity<PlayerRoleResponse> getPlayerRole(@RequestBody PlayerRoleRequest request) {
        return ResponseEntity.ok(playerService.getPlayerRole(request));
    }

    @PostMapping("/reconnect")
    public ResponseEntity<ReconnectResponse> reconnect(@Valid @RequestBody ReconnectRequest request) {
        return ResponseEntity.ok(playerService.reconnect(request));
    }
}

package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.PlayerRoleResponse;
import com.niko.ai_mafija_backend.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor

public class PlayerController {

    private final PlayerService playerService;

    @GetMapping("/{playerId}/role")
    public ResponseEntity<PlayerRoleResponse> getPlayerRole(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerRole(playerId));
    }
}
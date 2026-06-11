package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.ResolveNightResponse;
import com.niko.ai_mafija_backend.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @PostMapping
    public ResponseEntity<ActionResponse> submitAction(@RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.submitAction(request));
    }

    @PostMapping("/resolve-night/{lobbyCode}")
    public ResponseEntity<ResolveNightResponse> resolveNight(
            @PathVariable String lobbyCode) {

        return ResponseEntity.ok(
                actionService.resolveNight(lobbyCode)
        );
    }
}
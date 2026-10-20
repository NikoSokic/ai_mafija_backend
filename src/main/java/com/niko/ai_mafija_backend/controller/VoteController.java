package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Response.ResolveVoteResponse;
import com.niko.ai_mafija_backend.dto.Requests.VoteRequest;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor

public class VoteController {

    private final VoteService voteService;

    @PostMapping
    public ResponseEntity<VoteResponse> submitVote(
            @RequestBody VoteRequest request) {

        return ResponseEntity.ok(
                voteService.submitVote(request)
        );
    }

    @PostMapping("/resolve/{lobbyCode}")
    public ResponseEntity<ResolveVoteResponse> resolveVote(
            @PathVariable String lobbyCode,
            @Valid @RequestBody HostActionRequest request) {

        return ResponseEntity.ok(
                voteService.resolveVoteAsHost(lobbyCode, request)
        );
    }
}

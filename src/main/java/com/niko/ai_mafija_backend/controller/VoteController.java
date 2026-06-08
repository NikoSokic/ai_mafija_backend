package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.ResolveVoteResponse;
import com.niko.ai_mafija_backend.dto.VoteRequest;
import com.niko.ai_mafija_backend.dto.VoteResponse;
import com.niko.ai_mafija_backend.service.VoteService;
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
            @PathVariable String lobbyCode) {

        return ResponseEntity.ok(
                voteService.resolveVote(lobbyCode)
        );
    }
}
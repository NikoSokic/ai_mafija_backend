package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.dto.Response.RoleResponse;
import com.niko.ai_mafija_backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** Javni popis uloga za ekran "Nova soba" (umjesto /api/test/roles, koji ostaje radi kompatibilnosti) */
@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping("/api/roles")
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(role -> role.getId()))
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.getOpis()))
                .toList();
    }
}

package com.niko.ai_mafija_backend.controller;

import com.niko.ai_mafija_backend.model.Role;
import com.niko.ai_mafija_backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final RoleRepository roleRepository;

    @GetMapping("/api/test/roles")
    public List<Role> getRoles() {
        return roleRepository.findAll();
    }
}
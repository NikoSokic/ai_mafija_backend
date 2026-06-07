package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lobby_role")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder

public class LobbyRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "Lobby_id",nullable = false)
    private Lobby lobby;

    @OneToOne
    @JoinColumn(name = "role_id",nullable = false)
    private Role role;

    @Column(name = "role_count",nullable = false)
    private Integer roleCount;
}

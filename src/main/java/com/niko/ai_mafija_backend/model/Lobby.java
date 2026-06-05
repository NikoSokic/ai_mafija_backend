package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "lobby")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder

public class Lobby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String status;

    @Column(name = "max_players", nullable = false )
    private Integer maxPlayers;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @ManyToOne
    @JoinColumn(name = "host_player_id")
    private Player hostPlayer;





}

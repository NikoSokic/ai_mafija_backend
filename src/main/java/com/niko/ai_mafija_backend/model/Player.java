package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder

public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column( name = "is_host")
    private Boolean isHost;

    @Column( name = "is_alive")
    private Boolean isAlive;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @ManyToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "player_token", unique = true)
    private String playerToken;

    /** Kako je igrač ispao: NIGHT, VOTE ili LEFT (GameRules.ELIMINATED_*); null dok je živ. */
    @Column(name = "eliminated_how")
    private String eliminatedHow;

    @Column(name = "eliminated_round")
    private Integer eliminatedRound;

}

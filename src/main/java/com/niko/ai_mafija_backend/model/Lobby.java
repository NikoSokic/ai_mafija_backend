package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import com.niko.ai_mafija_backend.service.GameRules;

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

    /** Način igre: LIVE ili ONLINE (GameRules.MODE_*) */
    @Column(nullable = false)
    @Builder.Default
    private String mode = GameRules.MODE_LIVE;

    /** Javna soba se pojavljuje na popisu javnih soba */
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    /** Glas naratora */
    @Column(name = "voice_enabled", nullable = false)
    @Builder.Default
    private Boolean voiceEnabled = true;

}

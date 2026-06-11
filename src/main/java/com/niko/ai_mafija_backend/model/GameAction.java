package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "game_action")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder

public class GameAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "action_type")
    private String actionType;

    @Column(nullable = false)
    private String phase;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @ManyToOne
    @JoinColumn(name = "target_player_id")
    private Player targetPlayer;

    @ManyToOne
    @JoinColumn(name = "second_target_player_id")
    private Player secondTargetPlayer;


}

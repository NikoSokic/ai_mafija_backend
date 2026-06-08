package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "game_state")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder

public class GameState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phase;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "updated_at")
    private OffsetDateTime updateAt;

    @ManyToOne
    @JoinColumn(name = "current_turn_player_id")
    private Player currentTurnPlayer;

    @OneToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @Column(name = "tie_break_player_ids")
    private String tieBreakPlayerIds;
}

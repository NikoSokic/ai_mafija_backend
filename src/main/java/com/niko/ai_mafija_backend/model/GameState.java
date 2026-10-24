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

    @Column(name = "current_turn_role")
    private String currentTurnRole;

    @OneToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @Column(name = "tie_break_player_ids")
    private String tieBreakPlayerIds;

    /** Kad istječe trenutna faza ili potez noći; null = nema roka. GameTimerService tada pomiče igru dalje. */
    @Column(name = "phase_ends_at")
    private OffsetDateTime phaseEndsAt;

    /** Tko je stradao u zadnjoj noći (id-evi odvojeni zarezom), za jutarnji izvještaj. */
    @Column(name = "night_killed_player_ids")
    private String nightKilledPlayerIds;

    /** Tko je utišan u zadnjoj noći (id-evi odvojeni zarezom); zajedno s nightKilledPlayerIds čini lastNight. */
    @Column(name = "night_silenced_player_ids")
    private String nightSilencedPlayerIds;
}

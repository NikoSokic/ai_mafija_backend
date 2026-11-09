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

    /**
     * Početak trenutnog noćnog poteza (početak naracije). Iz njega se računa javni rok (GameRules.turnLatestEnd),
     * a phaseEndsAt je stvarni, tajni kraj poteza.
     */
    @Column(name = "turn_started_at")
    private OffsetDateTime turnStartedAt;

    /** Glumi li se trenutni potez (uloga je mrtva). Nikad se ne šalje klijentima. */
    @Column(name = "turn_fake")
    private Boolean turnFake;

    /** Drugi krug mafije: kandidati iz prvog kruga (id-evi odvojeni zarezom); null kad drugi krug ne traje */
    @Column(name = "mafia_runoff_player_ids")
    private String mafiaRunoffPlayerIds;

    /** Kad istječe drugi krug mafije */
    @Column(name = "mafia_runoff_ends_at")
    private OffsetDateTime mafiaRunoffEndsAt;

    /**
     * Meta mafije ove noći, kad je potez MAFIA završio (dogovor, jedina odabrana, nasumični kandidat drugog kruga
     * ili nasumični živi igrač). null = potez nije došao do kraja (domaćin je završio noć ranije).
     */
    @Column(name = "night_mafia_target_id")
    private Long nightMafiaTargetId;
}

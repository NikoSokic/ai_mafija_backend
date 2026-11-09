package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Trajanje jednog stvarnog noćnog poteza (od kraja naracije do kraja poteza); iz njih se glume potezi mrtvih uloga. */
@Entity
@Table(name = "turn_duration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnDuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GameRules.NIGHT_ORDER: MAFIA, DAMA, Doktor, Policajac, Novinar */
    @Column(nullable = false, length = 16)
    private String turn;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

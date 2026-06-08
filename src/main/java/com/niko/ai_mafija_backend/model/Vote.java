package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name ="vote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @ManyToOne
    @JoinColumn(name = "voter_player_id")
    private Player voterPlayer;

    @ManyToOne
    @JoinColumn(name = "target_player_id")
    private Player targetPlayer;

    @Column(name = "phase")
    private String phase;
}

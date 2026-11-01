package com.niko.ai_mafija_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Zahtjev da igrač na novom uređaju preuzme svoje mjesto u sobi; domaćin ga odobrava ili odbija. */
@Entity
@Table(name = "reclaim_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReclaimRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String CLAIMED = "CLAIMED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bez veze na tablice: brisanje igrača ili sobe ne ovisi o zahtjevima
    @Column(name = "lobby_id", nullable = false)
    private Long lobbyId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "claim_secret", nullable = false)
    private String claimSecret;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}

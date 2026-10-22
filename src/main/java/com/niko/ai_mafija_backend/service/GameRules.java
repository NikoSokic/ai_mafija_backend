package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.Player;

import java.time.Duration;
import java.util.List;

/**
 * Pravila igre na jednom mjestu: trajanja, faze, redoslijed noći i imena uloga.
 * Opis pravila za ljude je u frontend repou, docs/pravila-igre.md.
 */
public final class GameRules {

    private GameRules() {
    }

    // ---------- trajanja ----------
    public static final Duration DISCUSSION = Duration.ofSeconds(150);
    public static final Duration DISCUSSION_EXTENSION = Duration.ofSeconds(30);
    public static final Duration VOTING = Duration.ofSeconds(60);
    /**
     * Potez uloge traje uvijek isto (narator + odabir), i kad igrač odigra ranije i kad je uloga mrtva,
     * da se iz trajanja noći ne može zaključiti tko je živ.
     */
    public static final Duration NIGHT_TURN = Duration.ofSeconds(20);
    /** narator uspava prethodnu ulogu i budi ovu prije njezinog odabira */
    public static final Duration TURN_NARRATION = Duration.ofSeconds(6);
    /** prva noć: igrači još gledaju kartu uloge */
    public static final Duration FIRST_NIGHT_INTRO = Duration.ofSeconds(30);
    /** ostale noći: prijelaz i naracija */
    public static final Duration NIGHT_INTRO = Duration.ofSeconds(10);

    // ---------- faze ----------
    public static final String NIGHT = "NIGHT";
    /** rasprava; glasati se još ne može */
    public static final String DAY = "DAY";
    public static final String VOTING_PHASE = "VOTING";
    public static final String TIE_BREAK = "TIE_BREAK";
    public static final String FINISHED = "FINISHED";

    // ---------- statusi sobe ----------
    public static final String LOBBY_WAITING = "WAITING";
    public static final String LOBBY_IN_PROGRESS = "IN_PROGRESS";
    public static final String LOBBY_FINISHED = "FINISHED";

    // ---------- kako je igrač ispao ----------
    public static final String ELIMINATED_NIGHT = "NIGHT";
    public static final String ELIMINATED_VOTE = "VOTE";
    public static final String ELIMINATED_LEFT = "LEFT";

    // ---------- ishodi glasanja ----------
    public static final String VOTE_ELIMINATED = "ELIMINATED";
    public static final String VOTE_TIE = "TIE";
    public static final String VOTE_NO_ELIMINATION = "NO_ELIMINATION";

    // ---------- uloge (imena iz tablice role) ----------
    public static final String MAFIA = "Mafia";
    public static final String DAMA = "Dama";
    public static final String DOKTOR = "Doktor";
    public static final String POLICAJAC = "Policajac";
    public static final String NOVINAR = "Novinar";

    // ---------- noćni potezi ----------
    /** U potezu MAFIA ubija Mafija, a kad nje više nema – Dama. */
    public static final String TURN_MAFIA = "MAFIA";
    /** Dama utišava samo dok je Mafija živa; inače se potez glumi. */
    public static final String TURN_DAMA = "DAMA";
    public static final List<String> NIGHT_ORDER = List.of(TURN_MAFIA, TURN_DAMA, DOKTOR, POLICAJAC, NOVINAR);

    public static final String KILL = "KILL";
    public static final String SILENCE = "SILENCE";
    public static final String SAVE = "SAVE";
    public static final String INVESTIGATE = "INVESTIGATE";
    public static final String COMPARE = "COMPARE";

    public static boolean hasRole(Player player, String roleName) {
        return player.getRole() != null && roleName.equalsIgnoreCase(player.getRole().getName());
    }

    public static boolean isMafiaTeam(Player player) {
        return hasRole(player, MAFIA) || hasRole(player, DAMA);
    }

    public static boolean isAlive(Player player) {
        return Boolean.TRUE.equals(player.getIsAlive());
    }

    /** Glasanje je otvoreno u prvom glasanju i u ponovnom glasanju kod izjednačenja */
    public static boolean isVotingPhase(String phase) {
        return VOTING_PHASE.equals(phase) || TIE_BREAK.equals(phase);
    }

    /** Dan u širem smislu: rasprava i glasanje. Tada vrijede utišanost i jutarnji izvještaj. */
    public static boolean isDaytime(String phase) {
        return DAY.equals(phase) || isVotingPhase(phase);
    }

    /** Koju akciju uloga koristi u svom potezu */
    public static String actionTypeForTurn(String turn) {
        return switch (turn) {
            case TURN_MAFIA -> KILL;
            case TURN_DAMA -> SILENCE;
            case DOKTOR -> SAVE;
            case POLICAJAC -> INVESTIGATE;
            case NOVINAR -> COMPARE;
            default -> throw new IllegalArgumentException("Nepoznat noćni potez: " + turn);
        };
    }
}

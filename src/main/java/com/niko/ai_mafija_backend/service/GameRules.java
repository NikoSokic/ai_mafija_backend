package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Player;

import java.time.Duration;
import java.time.OffsetDateTime;
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
    /** narator uspava prethodnu ulogu i budi ovu prije njezinog odabira */
    public static final Duration TURN_NARRATION = Duration.ofSeconds(4);
    /** najdulji odabir (nakon naracije); tko do tada ne odigra, gubi potez */
    public static final Duration NIGHT_TURN_MAX = Duration.ofSeconds(13);
    /** pauza nakon što uloga odigra, nasumično ACTED_TAIL_MIN–ACTED_TAIL_MAX */
    public static final Duration ACTED_TAIL_MIN = Duration.ofSeconds(1);
    public static final Duration ACTED_TAIL_MAX = Duration.ofSeconds(4);
    /** drugi krug mafije kad mafijaši odaberu različite mete; birati se može samo između njih */
    public static final Duration MAFIA_RUNOFF_TIME = Duration.ofSeconds(10);
    /**
     * Glumljeni potez (uloga je mrtva) traje kao nasumični nedavni stvarni potez iste vrste (TurnDurationService).
     * Dok ih nema dovoljno, nasumično FAKE_TURN_FALLBACK_MIN–FAKE_TURN_FALLBACK_MAX.
     */
    public static final Duration FAKE_TURN_FALLBACK_MIN = Duration.ofSeconds(4);
    public static final Duration FAKE_TURN_FALLBACK_MAX = Duration.ofSeconds(10);
    /** glumljeni potez nikad ne traje kraće od ovoga (ni dulje od najduljeg mogućeg poteza te vrste) */
    public static final Duration FAKE_TURN_MIN = Duration.ofSeconds(3);
    /** glumljeni potez bira među toliko zadnjih stvarnih trajanja… */
    public static final int TURN_DURATION_SAMPLE = 100;
    /** …ali tek kad ih ima barem toliko */
    public static final int TURN_DURATION_MIN_SAMPLES = 10;
    /** koliko zadnjih trajanja se čuva po vrsti poteza */
    public static final int TURN_DURATION_KEEP = 500;
    /**
     * prva noć: igrači gledaju kartu uloge; uvod završava kad su svi živi spremni ili kad domaćin preskoči,
     * a ovo je sigurnosni rok da igra ne zapne ako netko zatvori aplikaciju
     */
    public static final Duration FIRST_NIGHT_INTRO_MAX = Duration.ofSeconds(90);
    /** ostale noći: prijelaz i naracija */
    public static final Duration NIGHT_INTRO = Duration.ofSeconds(10);

    // ---------- prisutnost igrača ----------
    /** Klijent se javlja otprilike toliko često (src/lib/useHeartbeat.ts) */
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    /** Igrač koji se ne javlja toliko je "Nije tu" i ne može preuzeti domaćina */
    public static final Duration AWAY_AFTER = Duration.ofSeconds(45);
    /** Čekaonica i kraj igre: domaćin se često zadrži u drugoj aplikaciji dok šalje kod sobe */
    public static final Duration HOST_ABSENT_LOBBY = Duration.ofSeconds(120);
    /** Usred igre: bez domaćina nema glasa naratora ni gumba domaćina */
    public static final Duration HOST_ABSENT_IN_GAME = Duration.ofSeconds(45);
    /** Soba u kojoj se toliko nitko nije javio je napuštena i briše se (LobbyCleanupService) */
    public static final Duration ABANDONED_AFTER = Duration.ofHours(1);

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

    // ---------- način igre ----------
    public static final String MODE_LIVE = "LIVE";
    public static final String MODE_ONLINE = "ONLINE";

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

    /** Uvod prve noći: igrači gledaju uloge i javljaju "Spreman sam"; nitko još nije na redu */
    public static boolean isFirstNightIntro(GameState gameState) {
        return gameState != null
                && NIGHT.equals(gameState.getPhase())
                && Integer.valueOf(1).equals(gameState.getRoundNumber())
                && gameState.getCurrentTurnRole() == null;
    }

    /** Dan u širem smislu: rasprava i glasanje. Tada vrijede utišanost i jutarnji izvještaj. */
    public static boolean isDaytime(String phase) {
        return DAY.equals(phase) || isVotingPhase(phase);
    }

    // ---------- rokovi noćnog poteza ----------
    // Potez počinje naracijom (turnStartedAt), pa slijedi odabir. Svi rokovi su null kad potez nema turnStartedAt.

    /** Najdulje moguće trajanje poteza nakon naracije: MAFIA ima i drugi krug */
    public static Duration turnMaxDuration(String turn) {
        return TURN_MAFIA.equals(turn) ? NIGHT_TURN_MAX.plus(MAFIA_RUNOFF_TIME) : NIGHT_TURN_MAX;
    }

    /** Kraj naracije: od tada uloga bira */
    public static OffsetDateTime actionStartedAt(GameState gameState) {
        return gameState.getTurnStartedAt() == null ? null : gameState.getTurnStartedAt().plus(TURN_NARRATION);
    }

    /** Rok prvog (za sve uloge jedinog) kruga odabira */
    public static OffsetDateTime firstRoundEndsAt(GameState gameState) {
        OffsetDateTime start = actionStartedAt(gameState);
        return start == null ? null : start.plus(NIGHT_TURN_MAX);
    }

    /** Najkasniji mogući kraj poteza ove vrste. To je i javni phaseEndsAt, i za živu i za mrtvu ulogu. */
    public static OffsetDateTime turnLatestEnd(GameState gameState) {
        OffsetDateTime start = actionStartedAt(gameState);
        return start == null ? null : start.plus(turnMaxDuration(gameState.getCurrentTurnRole()));
    }

    /** Traje li drugi krug mafije */
    public static boolean isMafiaRunoff(GameState gameState) {
        return gameState != null
                && NIGHT.equals(gameState.getPhase())
                && TURN_MAFIA.equals(gameState.getCurrentTurnRole())
                && gameState.getMafiaRunoffPlayerIds() != null;
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

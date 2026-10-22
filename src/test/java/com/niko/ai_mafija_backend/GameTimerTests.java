package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.service.GameTimerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** S uključenim timerom: istekla rasprava sama prelazi u glasanje */
@SpringBootTest(properties = "app.timers.enabled=true")
class GameTimerTests extends GameTestSupport {

    @Autowired private GameTimerService gameTimerService;

    @Test
    void expiredDiscussionMovesToVoting() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        GameState state = state(lobby, "DAY", 1);
        state.setPhaseEndsAt(OffsetDateTime.now().minusSeconds(1));
        gameStateRepository.save(state);

        gameTimerService.processExpiredPhases();

        assertEquals("VOTING", reload(lobby).getPhase());
    }
}

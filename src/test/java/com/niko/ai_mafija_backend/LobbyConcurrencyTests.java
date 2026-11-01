package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Istodobni ulasci u sobu: soba se ne smije prepuniti ni dobiti dva ista nadimka */
class LobbyConcurrencyTests extends GameTestSupport {

    private static final int ROUNDS = 10;
    private static final int THREADS = 8;
    private static final int CAPACITY = 4;

    @Test
    void concurrentJoinsNeverOverfillRoomOrDuplicateNickname() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Lobby lobby = lobby();
                lobby.setStatus("WAITING");
                lobby.setMaxPlayers(CAPACITY);
                lobbyRepository.save(lobby);
                String code = lobby.getCode();

                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    int n = t;
                    futures.add(pool.submit(() -> {
                        barrier.await();
                        JoinLobbyRequest request = new JoinLobbyRequest();
                        // pola dretvi šalje isti nadimak
                        request.setNickname(n % 2 == 0 ? "Isti" : "Igrac" + n);
                        try {
                            playerService.joinLobby(code, request);
                        } catch (RuntimeException expected) {
                            // soba puna ili nadimak zauzet
                        }
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get(15, TimeUnit.SECONDS);
                }

                List<Player> players = playerRepository.findByLobby(lobby);
                assertTrue(players.size() <= CAPACITY, "Soba prepunjena: " + players.size());
                assertEquals(CAPACITY, players.size());
                // 0 je moguće: četiri dretve s različitim nadimcima mogu popuniti sobu prije ostalih
                assertTrue(players.stream().filter(p -> p.getNickname().equals("Isti")).count() <= 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

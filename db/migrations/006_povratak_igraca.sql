-- Povratak igrača na novom uređaju (studeni 2026.): igrač upiše kod sobe i nadimak, a domaćin odobri zahtjev.
-- Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno u Supabase SQL editoru
-- PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Zahtjev traje kratko (2 min). lobby_id i player_id nisu strani ključevi namjerno: brisanje igrača ili sobe
-- ne smije ovisiti o ovoj tablici, a zaostali redovi istječu i brišu se pri sljedećem zahtjevu.
CREATE TABLE IF NOT EXISTS reclaim_request (
    id           bigserial PRIMARY KEY,
    lobby_id     bigint      NOT NULL,
    player_id    bigint      NOT NULL,
    -- tajna novog uređaja; samo tko je stvorio zahtjev može dohvatiti novi token
    claim_secret varchar(64) NOT NULL,
    -- PENDING, APPROVED, DENIED, CLAIMED ili EXPIRED
    status       varchar(16) NOT NULL,
    created_at   timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reclaim_request_lobby_status ON reclaim_request (lobby_id, status);

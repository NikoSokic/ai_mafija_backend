-- Sigurnost i ograničenja baze (studeni 2026.).
-- Backend se spaja kao vlasnik tablica, pa ga RLS ne ograničava. Sve ostalo u bazu ne smije preko javnog ključa.

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
ALTER TABLE lobby ENABLE ROW LEVEL SECURITY;
ALTER TABLE lobby_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE player ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE vote ENABLE ROW LEVEL SECURITY;
ALTER TABLE reclaim_request ENABLE ROW LEVEL SECURITY;
-- flyway_schema_history je u afterMigrate__rls_flyway.sql: zaključavanje iz migracije zaustavilo bi Flyway

-- Uloge anon i authenticated postoje samo na Supabaseu
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
            AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
        -- Supabase bi ih inače iznova davao svakoj novoj tablici
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
    END IF;
END $$;

-- Ono što entiteti već traže, a baza dosad nije provjeravala
ALTER TABLE lobby ALTER COLUMN status SET NOT NULL;
ALTER TABLE lobby ALTER COLUMN max_players SET NOT NULL;
ALTER TABLE player ALTER COLUMN lobby_id SET NOT NULL;
ALTER TABLE game_state ALTER COLUMN lobby_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS lobby_code_key ON lobby (code);
CREATE UNIQUE INDEX IF NOT EXISTS player_token_key ON player (player_token);
CREATE UNIQUE INDEX IF NOT EXISTS player_lobby_nickname_key ON player (lobby_id, lower(nickname));
CREATE UNIQUE INDEX IF NOT EXISTS game_state_lobby_key ON game_state (lobby_id);
-- Glas se ne mijenja: po jedan u svakoj fazi glasanja (VOTING i TIE_BREAK su odvojene)
CREATE UNIQUE INDEX IF NOT EXISTS vote_one_per_phase_key ON vote (lobby_id, round_number, phase, voter_player_id);

-- Strani ključevi: brisanje sobe i dohvat po sobi inače pretražuju cijelu tablicu
CREATE INDEX IF NOT EXISTS lobby_host_player_idx ON lobby (host_player_id);
CREATE INDEX IF NOT EXISTS lobby_role_lobby_idx ON lobby_role (lobby_id);
CREATE INDEX IF NOT EXISTS lobby_role_role_idx ON lobby_role (role_id);
CREATE INDEX IF NOT EXISTS player_lobby_idx ON player (lobby_id);
CREATE INDEX IF NOT EXISTS player_role_idx ON player (role_id);
CREATE INDEX IF NOT EXISTS game_action_lobby_idx ON game_action (lobby_id);
CREATE INDEX IF NOT EXISTS game_action_player_idx ON game_action (player_id);
CREATE INDEX IF NOT EXISTS game_action_target_idx ON game_action (target_player_id);
CREATE INDEX IF NOT EXISTS game_action_second_target_idx ON game_action (second_target_player_id);
CREATE INDEX IF NOT EXISTS vote_voter_idx ON vote (voter_player_id);
CREATE INDEX IF NOT EXISTS vote_target_idx ON vote (target_player_id);

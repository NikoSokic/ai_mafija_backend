-- Pravila dana, glasanja i noći (listopad 2026.)
-- Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno
-- u Supabase SQL editoru PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Rok trenutne faze ili poteza noći (rasprava 2:30, glasanje 60 s, potez uloge 30 s…)
ALTER TABLE game_state ADD COLUMN IF NOT EXISTS phase_ends_at timestamptz;
-- Tko je stradao u zadnjoj noći, za jutarnji izvještaj ("12,15")
ALTER TABLE game_state ADD COLUMN IF NOT EXISTS night_killed_player_ids text;
CREATE INDEX IF NOT EXISTS game_state_phase_ends_at_idx ON game_state (phase_ends_at);

-- Kako i kada je igrač ispao: NIGHT / VOTE / LEFT
ALTER TABLE player ADD COLUMN IF NOT EXISTS eliminated_how varchar(16);
ALTER TABLE player ADD COLUMN IF NOT EXISTS eliminated_round integer;

-- Igrač koji napusti sobu gubi token
ALTER TABLE player ALTER COLUMN player_token DROP NOT NULL;

-- "Suzdržan" glas nema metu
ALTER TABLE vote ALTER COLUMN target_player_id DROP NOT NULL;

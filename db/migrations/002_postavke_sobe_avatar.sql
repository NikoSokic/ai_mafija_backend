-- Postavke sobe i avatar igrača (listopad 2026.)
-- Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno
-- u Supabase SQL editoru PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Način igre: LIVE (uživo) ili ONLINE; postojeće sobe ostaju LIVE
ALTER TABLE lobby ADD COLUMN IF NOT EXISTS mode varchar(16) NOT NULL DEFAULT 'LIVE';
-- Javna soba se pojavljuje na popisu javnih soba
ALTER TABLE lobby ADD COLUMN IF NOT EXISTS is_public boolean NOT NULL DEFAULT false;
-- Glas naratora
ALTER TABLE lobby ADD COLUMN IF NOT EXISTS voice_enabled boolean NOT NULL DEFAULT true;
-- Popis javnih soba: status + javna, najnovije prve
CREATE INDEX IF NOT EXISTS lobby_public_waiting_idx ON lobby (status, is_public, created_at DESC);

-- Avatar igrača (jedan emoji, do 8 znakova jer složeni emoji ima više znakova)
ALTER TABLE player ADD COLUMN IF NOT EXISTS avatar_emoji varchar(8);

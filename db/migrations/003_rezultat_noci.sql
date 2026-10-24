-- Rezultat zadnje noći (listopad 2026.)
-- Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno
-- u Supabase SQL editoru PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Tko je utišan u zadnjoj noći ("12,15"), uz night_killed_player_ids; javlja se kao lastNight tijekom dana
ALTER TABLE game_state ADD COLUMN IF NOT EXISTS night_silenced_player_ids text;

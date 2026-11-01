-- Rezultat zadnje noći (listopad 2026.)

-- Tko je utišan u zadnjoj noći ("12,15"), uz night_killed_player_ids; javlja se kao lastNight tijekom dana
ALTER TABLE game_state ADD COLUMN IF NOT EXISTS night_silenced_player_ids text;

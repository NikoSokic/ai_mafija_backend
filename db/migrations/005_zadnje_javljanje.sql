-- Zadnje javljanje igrača (studeni 2026.): domaćin koji nestane predaje ulogu, a igrač koji se ne javlja
-- dobiva oznaku "Nije tu". Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno
-- u Supabase SQL editoru PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Klijent se javlja svakih 15 s (POST /api/players/heartbeat); null = još se nije javio od ove migracije
ALTER TABLE player ADD COLUMN IF NOT EXISTS last_seen_at timestamptz;

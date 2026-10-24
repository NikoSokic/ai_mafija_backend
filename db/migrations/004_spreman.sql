-- "Spreman sam" u uvodu prve noći (listopad 2026.)
-- Backend radi s spring.jpa.hibernate.ddl-auto=none, pa ovo treba pokrenuti ručno
-- u Supabase SQL editoru PRIJE pokretanja nove verzije backenda. Sigurno je pokrenuti više puta.

-- Igrač je pogledao svoju ulogu i spreman je; kad su svi živi spremni, prva noć počinje odmah
ALTER TABLE player ADD COLUMN IF NOT EXISTS is_ready boolean NOT NULL DEFAULT false;

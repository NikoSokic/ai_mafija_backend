-- "Spreman sam" u uvodu prve noći (listopad 2026.)

-- Igrač je pogledao svoju ulogu i spreman je; kad su svi živi spremni, prva noć počinje odmah
ALTER TABLE player ADD COLUMN IF NOT EXISTS is_ready boolean NOT NULL DEFAULT false;

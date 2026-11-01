-- Flywayeva tablica je u shemi public, pa i ona treba RLS. Pokreće se nakon svih migracija, pri svakom pokretanju.
-- U migraciji se ne smije: ALTER zaključa tablicu, a Flyway u nju za to vrijeme piše preko druge veze.
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;

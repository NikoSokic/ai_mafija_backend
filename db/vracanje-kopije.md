# Vraćanje sigurnosne kopije

Workflow `Sigurnosna kopija baze` svake noći izveze bazu, isproba vraćanje na privremenoj bazi, šifrira kopiju i čuva je 7 dana. Ako koji korak ne uspije, GitHub javlja neuspjeli workflow.

## Postavljanje

U GitHubu: Settings → Secrets and variables → Actions dodaj:

- `BACKUP_DB_URL`: `postgresql://postgres.PROJEKT:LOZINKA@aws-1-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require`
- `BACKUP_PASSPHRASE`: duga nasumična lozinka za šifriranje. Spremi je i izvan GitHuba, jer se bez nje kopije ne mogu vratiti.

Nakon promjene lozinke baze ažuriraj i `BACKUP_DB_URL`. GitHub gasi zakazane workflowe nakon 60 dana bez aktivnosti u javnom repou, pa ga tada treba ponovno uključiti u kartici Actions.

## Vraćanje

Vraćanje prepisuje podatke, pa ga prvo isprobaj na lokalnoj bazi. Na produkciji prethodno zaustavi backend.

1. U GitHubu otvori zadnji uspješni workflow i preuzmi `kopija-…`, zatim raspakiraj zip.
2. Dešifriraj:
   ```
   gpg --output kopija.dump --decrypt kopija.dump.gpg
   ```
3. Vrati kopiju. Ova naredba prvo **briše cijelu shemu `public`** u odredišnoj bazi, pa dobro provjeri `URL_BAZE`:
   ```
   pg_restore --dbname="URL_BAZE" --clean --if-exists --no-owner --no-privileges --exit-on-error kopija.dump
   ```
4. Pokreni backend i otvori `/api/roles`. Flyway u kopiji nalazi svoju tablicu i pokreće samo novije migracije.

Vraćene tablice nemaju prava za `anon` i `authenticated`, jer je kopija bez prava, a RLS ostaje uključen. To je željeno stanje. Na Supabaseu to potvrdi u Security Advisoru.

Obriši `kopija.dump` kad završiš, jer sadrži tokene igrača.

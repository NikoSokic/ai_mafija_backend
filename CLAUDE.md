# AI Mafija – backend

Spring Boot 4, Java 25, Gradle, PostgreSQL na Supabaseu. Frontend (`C:\Users\Niko\ai_mafija_frontend`) je gotov
i ovisi o ovom API-ju. Pravila igre su u `service/GameRules.java` (trajanja, faze, redoslijed noći), a opis za ljude
u frontend repou, `docs/pravila-igre.md`.

## Način rada

- **Prvo plan, pa kod.** Prije izmjena napiši kratak plan (što i zašto, koje datoteke).
- **Ne commitaj** i ne radi `git push` – commita vlasnik repoa.
- `./gradlew build` mora proći, uključujući testove, prije nego kažeš da je gotovo.

## Baza

- `spring.jpa.hibernate.ddl-auto=none`: Hibernate ne dira shemu.
- Svaka promjena sheme ide kao SQL u `db/migrations/NNN_opis.sql` (sljedeći slobodni broj, npr. `002_avatar.sql`).
  SQL mora biti idempotentan: `ADD COLUMN IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`…
- U istoj izmjeni ažuriraj i entitete u `model/`.
- Na kraju odgovora napiši da migraciju treba pokrenuti u **Supabase SQL Editoru** (prije pokretanja nove verzije backenda).
- Testovi rade na H2 (profil `test`, `src/test/resources/application-test.properties`, `create-drop`) i nikad ne
  diraju Supabase. `build.gradle` uključuje profil za sve testove, a testne klase imaju i `@ActiveProfiles("test")`.

## Tajne

- Lozinke, tokeni i ključevi nikad u kodu ni u `application*.properties` – samo env varijable
  (`SPRING_DATASOURCE_PASSWORD`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `APP_CORS_ALLOWED_ORIGINS`,
  `SUPABASE_ACCESS_TOKEN`…). `.env` je u `.gitignore`.

## API ugovor (frontend ga koristi)

- Postojeća polja u odgovorima (REST i WebSocket) se **ne mijenjaju ni ne brišu**, samo se dodaju nova.
  To vrijedi i za vrijednosti koje frontend uspoređuje: faze, tipove događaja, ishode glasanja, imena uloga.
- Greške: `ApiErrorResponse {timestamp, status, error, code, message, path}`. `code` je iz enuma
  `exception.ErrorCode`; frontend (`src/api/rest.ts`) prevodi poruke po kodu, pa se postojeći kodovi ne preimenuju.
  Nova greška = nova vrijednost u `ErrorCode` + odgovarajuća iznimka (`BadRequestException`, `ConflictException`…).
- WebSocket događaji šalju se tek **nakon commita**: uvijek preko `WebSocketService.publish...AfterCommit`
  (`@TransactionalEventListener(AFTER_COMMIT)`), nikad izravno preko `SimpMessagingTemplate` iz servisa.
- Sve što mijenja tijek igre zaključava red stanja igre (`GameStateRepository.findByLobbyForUpdate` / `findByIdForUpdate`).

## Testovi

- `GameTestSupport`: zajednička baza testova, briše bazu nakon svakog testa; `expire(lobby)` glumi istek roka
  (timer je u testovima isključen, `app.timers.enabled=false`).
- `FullGameApiTests`: cijela igra preko MockMvc-a, istim zahtjevima kao frontend.
- Ostali testovi pravila zovu servise izravno.

## Kraj svakog odgovora

1. **Kako provjeriti** – koraci u Swaggeru (`http://localhost:8080/swagger-ui/index.html`): koji endpoint, s kojim tijelom, što se očekuje.
2. Objašnjenje u 5–8 rečenica: što je promijenjeno i zašto.

# AI Mafija - Backend

AI Mafija je backend za višekorisničku igru Mafija s AI naratorom. Igrači se u stvarnom vremenu pridružuju sobama, preuzimaju uloge i igraju klasičnu igru Mafija, dok AI generira naraciju događaja i vodi tijek igre.

## Funkcionalnosti

- Kreiranje i upravljanje sobama za igranje
- Dodjela uloga igračima (mafija, građanin, detektiv i ostalo)
- Komunikacija u stvarnom vremenu putem WebSocketa
- AI narator koji prati tijek igre i generira naraciju događaja
- Pohrana podataka o igrama i igračima
- REST API za inicijalizaciju i upravljanje igrama

## Arhitektura sustava

Aplikacija je razvijena prema klijent-poslužitelj arhitekturi s podrškom za komunikaciju u stvarnom vremenu:

1. **Klijentska aplikacija** — igrači se spajaju na backend putem WebSocket veze i REST API-ja
2. **Spring Boot** — upravlja sobama, igračima, ulogama i logikom igre
3. **WebSocket sloj (STOMP)** — prenosi događaje igre u stvarnom vremenu svim igračima u sobi
4. **AI narator** — generira naraciju na temelju trenutnog stanja igre
5. **PostgreSQL baza** — pohranjuje podatke o igrama, sobama i igračima

## Korištene tehnologije

### Backend
- Java
- Spring Boot
- Spring Data JPA
- WebSocket (STOMP)
- Lombok
- Gradle

### Baza podataka
- PostgreSQL
- Supabase

### Dokumentacija
- SpringDoc OpenAPI (Swagger UI)

## Autor

**Niko Sokić**  
Fakultet elektrotehnike, strojarstva i brodogradnje (FESB)  
Sveučilište u Splitu

# JustFit — system zarządzania klubem fitness

Projekt na studia: aplikacja klient-serwer w Javie 21. Klient (JavaFX) łączy się z serwerem
przez gniazda TCP, serwer obsługuje wielu klientów jednocześnie (osobny wątek na połączenie)
i zapisuje dane w bazie H2. Obie aplikacje logują zdarzenia do plików (Log4j2) i mają testy (JUnit 5).

## Wymagania

- JDK 21 lub nowszy
- Maven 3.9+

## Uruchomienie

Raz, z głównego katalogu (buduje wszystko i uruchamia testy):

```
mvn install
```

Serwer z oknem (start/stop, lista klientów, logi na żywo; baza w `./data/justfit`):

```
mvn -pl server javafx:run
```

Serwer bez okna, z konsoli (domyślnie port 5000):

```
mvn -pl server exec:java
mvn -pl server exec:java -Dexec.args="6000 ./data/inna-baza"   # własny port i baza
```

Klient (w osobnym terminalu, można uruchomić wiele naraz):

```
mvn -pl client javafx:run
```

Domyślne konto: **admin / admin123** (tworzone przy pierwszym starcie na pustej bazie).
Zmień to hasło przed pokazaniem projektu jako gotowego.

Logi: `logs/server.log` i `logs/client.log` (katalog, z którego uruchomiono aplikację).

## Struktura

| Moduł | Zawartość |
|---|---|
| `common` | model (`Member`), protokół (`Request`, `Response`, `Actions`), wspólny Gson |
| `server` | `JustFitServer` (pętla accept + pula wątków), `ClientHandler`, `RequestHandler` (logika), `db/` (H2, DAO, BCrypt), `gui/` (okno serwera JavaFX) |
| `client` | `JustFitApp` (JavaFX), `MemberService` (typowane wywołania), `ServerConnection` (gniazdo) |

## Protokół

Jedna linia = jeden komunikat JSON (UTF-8). Klient wysyła `{"action":"...","data":{...}}`,
serwer odpowiada `{"ok":true|false,"error":"...","data":...}`. Akcje: `PING`, `LOGIN`,
`LIST_MEMBERS`, `ADD_MEMBER`, `UPDATE_MEMBER`, `DELETE_MEMBER`. Poza `PING` i `LOGIN`
każda akcja wymaga wcześniejszego zalogowania na tym samym połączeniu.

## Stan prac

Zrobione: serwer wielowątkowy, baza i hasła BCrypt, logi, protokół, klient JavaFX
(logowanie, lista, dodawanie, edycja, usuwanie członków z datą ważności karnetu),
okno serwera (start/stop, lista klientów, logi na żywo), testy.

Do zrobienia:
- rejestracja obecności (wejścia na klub)
- osobne konta/role (admin, recepcja), zmiana hasła
- dokumentacja końcowa

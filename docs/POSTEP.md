# Notatki postępu (JustFit)

Plik służy do wznawiania pracy po przerwie. Aktualizowany przy każdym kroku, razem z commitem.

## Co to jest

Projekt na studia: klient-serwer w Javie 21 (gniazda TCP, serwer wielowątkowy, JavaFX, baza H2,
Log4j2, JUnit 5). Termin: grudzień 2026. Autor będzie projekt bronił ustnie.

## Wymagania i decyzje (od autora)

- Prowadzący zasugerował rozszerzenie: **zapisy na zajęcia z trenerem**.
- Kod ma być **prosty** (autor go broni): bez nowych bibliotek, ten sam wzorzec co reszta
  (jedna klasa DAO na tabelę, jeden `switch` z akcjami, krótkie klasy, komentarze po polsku).
- Ma działać **na każdym komputerze od razu**: jeden plik `.jar` serwera i klienta, wystarczy Java 21.
- **Ładne UI**: JavaFX + jeden plik CSS, bez dodatkowych bibliotek.
- Zapisuje **recepcja w imieniu członka** (jedna aplikacja dla pracowników, bez kont członków).
- Zapisy na zajęcia **zastępują** rejestrację obecności (wrócić do niej tylko, jeśli zostanie czas).
- Plik PDF z propozycją tematu **zostaje bez zmian**.

## Reguły zapisów

- nie można przekroczyć limitu miejsc,
- ten sam członek nie zapisze się dwa razy na te same zajęcia,
- nie można zapisać na zajęcia z przeszłości,
- karnet członka musi być ważny w dniu zajęć,
- zapis można anulować,
- trenera z zaplanowanymi zajęciami nie można usunąć; usunięcie zajęć lub członka usuwa jego zapisy.

Wyścig o ostatnie miejsce: zapis wykonuje się w sekcji krytycznej (`synchronized` na wspólnym
obiekcie w `BookingDao`). Wystarcza, bo działa jeden proces serwera.

## Plan kroków

| Krok | Zakres | Status |
|---|---|---|
| 0 | szkielet: serwer, klient, baza, logi, testy, okno serwera | zrobione, zbudowane u autora |
| A | serwer: trenerzy, zajęcia, zapisy (modele, tabele, DAO, `ClassHandler`, testy) | kod gotowy i wypchnięty; czeka na `mvn install` u autora |
| B | klient: zakładki Członkowie / Zajęcia (z uczestnikami i zapisami) / Trenerzy | kod gotowy i wypchnięty; czeka na `mvn install` u autora |
| C | stylowanie CSS (pasek zapełnienia zajęć, odznaki statusu, kafelki, nagłówek) | kod gotowy i wypchnięty; czeka na oględziny u autora |
| D | pliki `.jar` serwera i klienta (`java -jar`), skrypty startowe | do zrobienia |
| E | dokumentacja, schemat architektury do obrony, README | do zrobienia |

## Protokół (dodatki w kroku A)

`LIST_TRAINERS`, `ADD_TRAINER`, `DELETE_TRAINER`, `LIST_SESSIONS`, `ADD_SESSION`, `DELETE_SESSION`,
`LIST_BOOKINGS` (dane: `sessionId`), `BOOK` (`sessionId`, `memberId`), `CANCEL_BOOKING` (`bookingId`).
Daty zajęć jako tekst ISO `RRRR-MM-DDTGG:MM`.

## Jak pracujemy

- Każdy krok to osobny commit. Autor po każdym kroku robi `git pull` i `mvn install`
  i wkleja wynik, jeśli coś nie przechodzi (w środowisku Claude'a Maven nie ma dostępu do bibliotek,
  więc kod jest sprawdzany dopiero u autora).
- Uruchamianie: `mvn install`, potem `mvn -pl server javafx:run` (okno serwera)
  i `mvn -pl client javafx:run` (klient). Konto: `admin` / `admin123`.

## Wznowienie po przerwie

1. Przeczytaj ten plik i `README.md`.
2. `git log --oneline` pokazuje, który krok jest ostatni.
3. Weź pierwszy krok ze statusem „w trakcie" albo „do zrobienia" i kontynuuj.

## Dziennik

- 2026-11-04: szkielet i okno serwera wypchnięte; autor zbudował `mvn install` bez błędów.
  Uzgodniono plan zapisów na zajęcia (kroki A-E). Start kroku A.
- 2026-11-04: krok A napisany: modele `Trainer`, `ClassSession`, `Booking` (common); tabele, `TrainerDao`,
  `SessionDao`, `BookingDao`, `BusinessRuleException`, `ClassHandler` (server). Testy: `ClassesDaoTest`
  (reguły i wyścig w bazie), `ClassHandlerTest` (przez protokół), wyścig przez gniazda w `ServerIntegrationTest`.
  Sprawdzone tutaj: kompilacja `common` + test dymny; reszta dopiero u autora (`git pull`, `mvn install`).
  Następny krok: B (klient), po potwierdzeniu, że A się buduje.
- 2026-11-05: krok B: klient ma trzy zakładki. Zmiana względem planu: zapisy są w zakładce **Zajęcia**
  (u góry grafik, pod spodem uczestnicy zaznaczonych zajęć, przyciski „Zapisz członka" i „Anuluj zapis"),
  a trenerzy mają osobną zakładkę. Powód: prostszy przepływ i mniej kodu do obrony.
  Nowe klasy klienta: `ClubService` (dawne `MemberService`, wszystkie wywołania serwera), `Ui` (wywołania
  w tle, okna komunikatów, kolumny tabel), `MembersTab`, `SessionsTab`, `TrainersTab`, `Refreshable`.
  `Member.toString()` w common (lista wyboru). Test `ClubServiceTest` rozszerzony o trenerów, zajęcia i zapisy.
  Sprawdzone tutaj: kompilacja `common` i składnia klienta; reszta dopiero u autora.
  Pomysły na później: wyszukiwarka w oknie wyboru członka (teraz zwykła lista rozwijana),
  stylowanie w kroku C.
- 2026-11-05: krok C (wygląd): wspólny arkusz `common/src/main/resources/justfit.css` (kolory zdefiniowane raz
  na `.root`), używany przez klienta i okno serwera. Klient: karta logowania na środku, ciemny nagłówek
  z przyciskiem „Wyloguj", kafelki ze statystykami (`StatTile`), kolorowe odznaki statusu karnetu
  (`Ui.badgeColumn`), pasek zapełnienia zajęć (zielony / pomarańczowy od 80% / czerwony gdy pełne),
  przyciski „primary" i „danger", ostylowane okna dialogowe (`Ui.style`). Okno serwera w tym samym stylu.
  Błędy w CSS JavaFX tylko loguje, więc aplikacja uruchomi się nawet z literówką w stylu.
  Po zmianie CSS trzeba zrobić `mvn install` (CSS jest w module common).

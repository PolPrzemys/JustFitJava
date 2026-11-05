package pl.justfit.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Członek klubu wraz z datą ważności karnetu.
 * Data jest przesyłana jako tekst ISO (RRRR-MM-DD), puste pole oznacza brak karnetu.
 */
public class Member {

    private long id;
    private String name;
    private String membershipNumber;
    private String expirationDate;

    public Member() {
    }

    public Member(long id, String name, String membershipNumber, String expirationDate) {
        this.id = id;
        this.name = name;
        this.membershipNumber = membershipNumber;
        this.expirationDate = expirationDate;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMembershipNumber() {
        return membershipNumber;
    }

    public void setMembershipNumber(String membershipNumber) {
        this.membershipNumber = membershipNumber;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    /** Zwraca datę ważności albo null, gdy brak karnetu. Rzuca wyjątek dla niepoprawnego formatu. */
    public LocalDate expirationAsDate() throws DateTimeParseException {
        if (expirationDate == null || expirationDate.isBlank()) {
            return null;
        }
        return LocalDate.parse(expirationDate);
    }

    /** Tekst na listach wyboru, np. "Jan Kowalski (K-001)". */
    @Override
    public String toString() {
        return name + " (" + membershipNumber + ")";
    }

    /** Status do wyświetlenia w tabeli. */
    public String getStatus() {
        try {
            LocalDate date = expirationAsDate();
            if (date == null) {
                return "Brak karnetu";
            }
            return date.isBefore(LocalDate.now()) ? "Wygasł" : "Aktywny";
        } catch (DateTimeParseException e) {
            return "Błędna data";
        }
    }
}

package pl.justfit.server.db;

/** Naruszenie reguły biznesowej (np. brak miejsc). Komunikat jest po polsku i nadaje się do pokazania użytkownikowi. */
public class BusinessRuleException extends Exception {

    public BusinessRuleException(String message) {
        super(message);
    }
}

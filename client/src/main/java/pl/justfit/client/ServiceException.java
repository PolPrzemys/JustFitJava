package pl.justfit.client;

/** Serwer odpowiedział błędem (np. złe hasło, zduplikowany numer). Komunikat nadaje się do pokazania użytkownikowi. */
public class ServiceException extends Exception {

    public ServiceException(String message) {
        super(message);
    }
}

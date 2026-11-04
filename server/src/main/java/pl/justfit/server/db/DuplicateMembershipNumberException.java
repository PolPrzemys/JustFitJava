package pl.justfit.server.db;

/** Numer karnetu jest już przypisany innemu członkowi. */
public class DuplicateMembershipNumberException extends Exception {

    public DuplicateMembershipNumberException(String membershipNumber, Throwable cause) {
        super("Numer karnetu już istnieje: " + membershipNumber, cause);
    }
}

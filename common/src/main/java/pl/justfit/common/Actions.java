package pl.justfit.common;

/** Nazwy akcji protokołu klient-serwer. */
public final class Actions {

    public static final String PING = "PING";
    public static final String LOGIN = "LOGIN";
    public static final String LIST_MEMBERS = "LIST_MEMBERS";
    public static final String ADD_MEMBER = "ADD_MEMBER";
    public static final String UPDATE_MEMBER = "UPDATE_MEMBER";
    public static final String DELETE_MEMBER = "DELETE_MEMBER";

    // trenerzy, zajęcia i zapisy
    public static final String LIST_TRAINERS = "LIST_TRAINERS";
    public static final String ADD_TRAINER = "ADD_TRAINER";
    public static final String DELETE_TRAINER = "DELETE_TRAINER";
    public static final String LIST_SESSIONS = "LIST_SESSIONS";
    public static final String ADD_SESSION = "ADD_SESSION";
    public static final String DELETE_SESSION = "DELETE_SESSION";
    public static final String LIST_BOOKINGS = "LIST_BOOKINGS";
    public static final String BOOK = "BOOK";
    public static final String CANCEL_BOOKING = "CANCEL_BOOKING";

    private Actions() {
    }
}

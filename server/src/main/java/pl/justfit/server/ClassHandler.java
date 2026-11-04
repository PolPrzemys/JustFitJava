package pl.justfit.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.common.Actions;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.common.Trainer;
import pl.justfit.server.db.BookingDao;
import pl.justfit.server.db.BusinessRuleException;
import pl.justfit.server.db.Database;
import pl.justfit.server.db.SessionDao;
import pl.justfit.server.db.TrainerDao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Akcje związane z trenerami, zajęciami i zapisami. RequestHandler sprawdza logowanie
 * i przekazuje tu żądania; ta klasa waliduje dane i woła odpowiednie DAO.
 */
public class ClassHandler {

    private static final Logger log = LogManager.getLogger(ClassHandler.class);
    private static final int MAX_CAPACITY = 500;

    private final TrainerDao trainers;
    private final SessionDao sessions;
    private final BookingDao bookings;

    public ClassHandler(Database database) {
        this.trainers = new TrainerDao(database);
        this.sessions = new SessionDao(database);
        this.bookings = new BookingDao(database);
    }

    public Response handle(Request request, String username) throws SQLException, BusinessRuleException {
        return switch (request.getAction()) {
            case Actions.LIST_TRAINERS -> Response.ok(trainers.list());
            case Actions.ADD_TRAINER -> addTrainer(request, username);
            case Actions.DELETE_TRAINER -> deleteTrainer(request, username);
            case Actions.LIST_SESSIONS -> Response.ok(sessions.list());
            case Actions.ADD_SESSION -> addSession(request, username);
            case Actions.DELETE_SESSION -> deleteSession(request, username);
            case Actions.LIST_BOOKINGS -> listBookings(request);
            case Actions.BOOK -> book(request, username);
            case Actions.CANCEL_BOOKING -> cancelBooking(request, username);
            default -> Response.error("Nieznana akcja: " + request.getAction());
        };
    }

    // ---------- trenerzy ----------

    private Response addTrainer(Request request, String username) throws SQLException {
        Trainer trainer = request.get("trainer", Trainer.class);
        if (trainer == null || trainer.getName() == null || trainer.getName().isBlank()) {
            return Response.error("Imię i nazwisko trenera jest wymagane");
        }
        Trainer saved = trainers.add(trainer);
        log.info("Dodano trenera id={} '{}' (przez '{}')", saved.getId(), saved.getName(), username);
        return Response.ok(saved);
    }

    private Response deleteTrainer(Request request, String username) throws SQLException, BusinessRuleException {
        Long id = request.get("id", Long.class);
        if (id == null) {
            return Response.error("Brak identyfikatora trenera");
        }
        if (!trainers.delete(id)) {
            return Response.error("Nie znaleziono trenera");
        }
        log.info("Usunięto trenera id={} (przez '{}')", id, username);
        return Response.ok();
    }

    // ---------- zajęcia ----------

    private Response addSession(Request request, String username) throws SQLException, BusinessRuleException {
        ClassSession session = request.get("session", ClassSession.class);
        String problem = validate(session);
        if (problem != null) {
            return Response.error(problem);
        }
        ClassSession saved = sessions.add(session);
        log.info("Dodano zajęcia id={} '{}' {} (przez '{}')", saved.getId(), saved.getTitle(),
                saved.getStartTime(), username);
        return Response.ok(saved);
    }

    private Response deleteSession(Request request, String username) throws SQLException {
        Long id = request.get("id", Long.class);
        if (id == null) {
            return Response.error("Brak identyfikatora zajęć");
        }
        if (!sessions.delete(id)) {
            return Response.error("Nie znaleziono zajęć");
        }
        log.info("Usunięto zajęcia id={} razem z zapisami (przez '{}')", id, username);
        return Response.ok();
    }

    /** @return opis problemu albo null, gdy dane są poprawne */
    private String validate(ClassSession session) {
        if (session == null) {
            return "Brak danych zajęć";
        }
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            return "Nazwa zajęć jest wymagana";
        }
        if (session.getRoom() == null || session.getRoom().isBlank()) {
            return "Sala jest wymagana";
        }
        if (session.getCapacity() < 1 || session.getCapacity() > MAX_CAPACITY) {
            return "Limit miejsc musi być liczbą od 1 do " + MAX_CAPACITY;
        }
        if (session.getStartTime() == null || session.getStartTime().isBlank()) {
            return "Termin zajęć jest wymagany";
        }
        try {
            if (session.startAsDateTime().isBefore(LocalDateTime.now())) {
                return "Termin zajęć nie może być w przeszłości";
            }
        } catch (DateTimeParseException e) {
            return "Termin musi mieć format RRRR-MM-DDTGG:MM";
        }
        return null;
    }

    // ---------- zapisy ----------

    private Response listBookings(Request request) throws SQLException {
        Long sessionId = request.get("sessionId", Long.class);
        if (sessionId == null) {
            return Response.error("Brak identyfikatora zajęć");
        }
        return Response.ok(bookings.listForSession(sessionId));
    }

    private Response book(Request request, String username) throws SQLException, BusinessRuleException {
        Long sessionId = request.get("sessionId", Long.class);
        Long memberId = request.get("memberId", Long.class);
        if (sessionId == null || memberId == null) {
            return Response.error("Wybierz zajęcia i członka");
        }
        Booking booking = bookings.book(sessionId, memberId);
        log.info("Zapis id={}: członek {} na zajęcia {} (przez '{}')", booking.getId(), memberId, sessionId, username);
        return Response.ok(booking);
    }

    private Response cancelBooking(Request request, String username) throws SQLException {
        Long bookingId = request.get("bookingId", Long.class);
        if (bookingId == null) {
            return Response.error("Brak identyfikatora zapisu");
        }
        if (!bookings.cancel(bookingId)) {
            return Response.error("Nie znaleziono zapisu");
        }
        log.info("Anulowano zapis id={} (przez '{}')", bookingId, username);
        return Response.ok();
    }
}

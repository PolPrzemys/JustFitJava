package pl.justfit.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.common.Actions;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.server.db.Database;
import pl.justfit.server.db.DuplicateMembershipNumberException;
import pl.justfit.server.db.MemberDao;
import pl.justfit.server.db.UserDao;

import java.sql.SQLException;
import java.time.format.DateTimeParseException;

/**
 * Logika obsługi żądań jednego połączenia. Każdy klient ma własną instancję,
 * więc stan zalogowania nie jest współdzielony między wątkami.
 */
public class RequestHandler {

    private static final Logger log = LogManager.getLogger(RequestHandler.class);

    private final UserDao users;
    private final MemberDao members;
    private String username;

    public RequestHandler(Database database) {
        this.users = new UserDao(database);
        this.members = new MemberDao(database);
    }

    public boolean isAuthenticated() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }

    public Response handle(Request request) {
        if (request == null || request.getAction() == null) {
            return Response.error("Nieprawidłowe żądanie");
        }
        try {
            String action = request.getAction();
            if (Actions.PING.equals(action)) {
                return Response.ok();
            }
            if (Actions.LOGIN.equals(action)) {
                return login(request);
            }
            if (!isAuthenticated()) {
                log.warn("Odrzucono akcję {} bez logowania", action);
                return Response.error("Wymagane logowanie");
            }
            return switch (action) {
                case Actions.LIST_MEMBERS -> Response.ok(members.list());
                case Actions.ADD_MEMBER -> addMember(request);
                case Actions.UPDATE_MEMBER -> updateMember(request);
                case Actions.DELETE_MEMBER -> deleteMember(request);
                default -> Response.error("Nieznana akcja: " + action);
            };
        } catch (DuplicateMembershipNumberException e) {
            log.info("Konflikt numeru karnetu: {}", e.getMessage());
            return Response.error("Ten numer karnetu jest już używany");
        } catch (SQLException e) {
            log.error("Błąd bazy danych przy akcji {}", request.getAction(), e);
            return Response.error("Błąd bazy danych");
        } catch (RuntimeException e) {
            log.error("Nieoczekiwany błąd przy akcji {}", request.getAction(), e);
            return Response.error("Błąd serwera");
        }
    }

    private Response login(Request request) throws SQLException {
        String user = request.getString("username");
        String password = request.getString("password");
        if (users.authenticate(user, password)) {
            this.username = user;
            log.info("Zalogowano użytkownika '{}'", user);
            return Response.ok();
        }
        log.warn("Nieudane logowanie na konto '{}'", user);
        return Response.error("Nieprawidłowy login lub hasło");
    }

    private Response addMember(Request request) throws SQLException, DuplicateMembershipNumberException {
        Member member = request.get("member", Member.class);
        String problem = validate(member);
        if (problem != null) {
            return Response.error(problem);
        }
        Member saved = members.add(member);
        log.info("Dodano członka id={} nr={} (przez '{}')", saved.getId(), saved.getMembershipNumber(), username);
        return Response.ok(saved);
    }

    private Response updateMember(Request request) throws SQLException, DuplicateMembershipNumberException {
        Member member = request.get("member", Member.class);
        String problem = validate(member);
        if (problem != null) {
            return Response.error(problem);
        }
        if (!members.update(member)) {
            return Response.error("Nie znaleziono członka");
        }
        log.info("Zmieniono członka id={} (przez '{}')", member.getId(), username);
        return Response.ok(member);
    }

    private Response deleteMember(Request request) throws SQLException {
        Long id = request.get("id", Long.class);
        if (id == null) {
            return Response.error("Brak identyfikatora członka");
        }
        if (!members.delete(id)) {
            return Response.error("Nie znaleziono członka");
        }
        log.info("Usunięto członka id={} (przez '{}')", id, username);
        return Response.ok();
    }

    /** @return opis problemu albo null, gdy dane są poprawne */
    private String validate(Member member) {
        if (member == null) {
            return "Brak danych członka";
        }
        if (member.getName() == null || member.getName().isBlank()) {
            return "Imię i nazwisko jest wymagane";
        }
        if (member.getMembershipNumber() == null || member.getMembershipNumber().isBlank()) {
            return "Numer karnetu jest wymagany";
        }
        try {
            member.expirationAsDate();
        } catch (DateTimeParseException e) {
            return "Data ważności musi mieć format RRRR-MM-DD";
        }
        return null;
    }
}

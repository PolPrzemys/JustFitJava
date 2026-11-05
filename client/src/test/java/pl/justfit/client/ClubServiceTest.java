package pl.justfit.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.justfit.common.Actions;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Json;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.common.Trainer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testy klienta na atrapie serwera: nie potrzebują prawdziwego serwera ani bazy. */
@Timeout(30)
class ClubServiceTest {

    private ServerSocket fakeServer;
    private Thread serverThread;

    @BeforeEach
    void startFakeServer() throws IOException {
        fakeServer = new ServerSocket(0);
        serverThread = new Thread(this::serve, "fake-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stopFakeServer() throws IOException {
        fakeServer.close();
    }

    /** Odpowiada według prostego scenariusza: hasło "dobre" przechodzi, reszta to dane na sztywno. */
    private void serve() {
        try (Socket socket = fakeServer.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Response response = answer(Json.fromJson(line, Request.class));
                out.write(Json.toJson(response));
                out.newLine();
                out.flush();
            }
        } catch (IOException ignored) {
            // test zamyka gniazdo
        }
    }

    private Response answer(Request request) {
        return switch (request.getAction()) {
            case Actions.LOGIN -> "dobre".equals(request.getString("password"))
                    ? Response.ok() : Response.error("Nieprawidłowy login lub hasło");

            case Actions.LIST_MEMBERS -> Response.ok(List.of(
                    new Member(1, "Jan Kowalski", "K-001", "2030-01-01"),
                    new Member(2, "Ala Nowak", "K-002", null)));
            case Actions.ADD_MEMBER -> {
                Member member = request.get("member", Member.class);
                member.setId(42);
                yield Response.ok(member);
            }
            case Actions.DELETE_MEMBER -> Response.error("Nie znaleziono członka");

            case Actions.LIST_TRAINERS -> Response.ok(List.of(
                    new Trainer(1, "Anna Trener"), new Trainer(2, "Bartek Siła")));
            case Actions.ADD_TRAINER -> {
                Trainer trainer = request.get("trainer", Trainer.class);
                trainer.setId(5);
                yield Response.ok(trainer);
            }
            case Actions.DELETE_TRAINER ->
                    Response.error("Trener ma zaplanowane zajęcia. Najpierw usuń te zajęcia.");

            case Actions.LIST_SESSIONS -> Response.ok(List.of(
                    new ClassSession(1, "Joga", 1, "Anna Trener", "Sala A", "2030-05-01T18:00", 10, 4)));
            case Actions.ADD_SESSION -> {
                ClassSession session = request.get("session", ClassSession.class);
                session.setId(9);
                session.setTrainerName("Anna Trener");
                session.setBooked(0);
                yield Response.ok(session);
            }
            case Actions.DELETE_SESSION -> Response.ok();

            case Actions.LIST_BOOKINGS -> Response.ok(request.get("sessionId", Long.class) == 1L
                    ? List.of(new Booking(10, 1, 1, "Jan Kowalski", "K-001"),
                              new Booking(11, 1, 2, "Ala Nowak", "K-002"))
                    : List.of());
            case Actions.BOOK -> {
                long sessionId = request.get("sessionId", Long.class);
                long memberId = request.get("memberId", Long.class);
                yield memberId == 2
                        ? Response.error("Brak wolnych miejsc")
                        : Response.ok(new Booking(77, sessionId, memberId, "Jan Kowalski", "K-001"));
            }
            case Actions.CANCEL_BOOKING -> Response.ok();

            default -> Response.error("Nieznana akcja");
        };
    }

    private ClubService connect() throws IOException {
        return new ClubService(new ServerConnection("localhost", fakeServer.getLocalPort(), 5000));
    }

    // ---------- logowanie i członkowie ----------

    @Test
    void loginSucceedsWithGoodPassword() throws Exception {
        try (ClubService service = connect()) {
            service.login("admin", "dobre");
        }
    }

    @Test
    void loginFailureSurfacesServerMessage() throws Exception {
        try (ClubService service = connect()) {
            ServiceException error = assertThrows(ServiceException.class, () -> service.login("admin", "zle"));
            assertEquals("Nieprawidłowy login lub hasło", error.getMessage());
        }
    }

    @Test
    void listMembersParsesTypedList() throws Exception {
        try (ClubService service = connect()) {
            List<Member> members = service.listMembers();
            assertEquals(2, members.size());
            assertEquals("Jan Kowalski", members.get(0).getName());
            assertEquals("Brak karnetu", members.get(1).getStatus());
            assertEquals("Jan Kowalski (K-001)", members.get(0).toString());
        }
    }

    @Test
    void addMemberReturnsMemberWithServerAssignedId() throws Exception {
        try (ClubService service = connect()) {
            Member saved = service.addMember(new Member(0, "Ewa", "K-003", null));
            assertEquals(42, saved.getId());
            assertEquals("Ewa", saved.getName());
        }
    }

    @Test
    void errorFromServerBecomesServiceException() throws Exception {
        try (ClubService service = connect()) {
            assertThrows(ServiceException.class, () -> service.deleteMember(7));
        }
    }

    // ---------- trenerzy ----------

    @Test
    void trainersAreListedAndAdded() throws Exception {
        try (ClubService service = connect()) {
            List<Trainer> trainers = service.listTrainers();
            assertEquals(2, trainers.size());
            assertEquals("Bartek Siła", trainers.get(1).getName());

            Trainer saved = service.addTrainer(new Trainer(0, "Ola"));
            assertEquals(5, saved.getId());
            assertEquals("Ola", saved.getName());
        }
    }

    @Test
    void deletingTrainerWithSessionsShowsServerExplanation() throws Exception {
        try (ClubService service = connect()) {
            ServiceException error = assertThrows(ServiceException.class, () -> service.deleteTrainer(1));
            assertTrue(error.getMessage().contains("zaplanowane zajęcia"));
        }
    }

    // ---------- zajęcia i zapisy ----------

    @Test
    void sessionsAreListedWithTrainerAndFreeSpots() throws Exception {
        try (ClubService service = connect()) {
            List<ClassSession> sessions = service.listSessions();
            assertEquals(1, sessions.size());
            ClassSession session = sessions.get(0);
            assertEquals("Joga", session.getTitle());
            assertEquals("Anna Trener", session.getTrainerName());
            assertEquals(6, session.getFreeSpots());
            assertEquals(2030, session.startAsDateTime().getYear());
        }
    }

    @Test
    void addedSessionComesBackWithServerAssignedData() throws Exception {
        try (ClubService service = connect()) {
            ClassSession sent = new ClassSession(0, "Pilates", 1, null, "Sala B", "2030-06-01T10:00", 8, 0);
            ClassSession saved = service.addSession(sent);
            assertEquals(9, saved.getId());
            assertEquals("Pilates", saved.getTitle());
            assertEquals("Sala B", saved.getRoom());
            assertEquals("Anna Trener", saved.getTrainerName());
            assertEquals(8, saved.getCapacity());
        }
    }

    @Test
    void bookingsAreListedPerSession() throws Exception {
        try (ClubService service = connect()) {
            List<Booking> bookings = service.listBookings(1);
            assertEquals(2, bookings.size());
            assertEquals("Ala Nowak", bookings.get(1).getMemberName());
            assertTrue(service.listBookings(2).isEmpty());
        }
    }

    @Test
    void bookSendsBothIdsAndReturnsBooking() throws Exception {
        try (ClubService service = connect()) {
            Booking booking = service.book(3, 1);
            assertEquals(77, booking.getId());
            assertEquals(3, booking.getSessionId());
            assertEquals(1, booking.getMemberId());
        }
    }

    @Test
    void fullSessionErrorReachesTheCaller() throws Exception {
        try (ClubService service = connect()) {
            ServiceException error = assertThrows(ServiceException.class, () -> service.book(1, 2));
            assertEquals("Brak wolnych miejsc", error.getMessage());
        }
    }

    @Test
    void cancelAndDeleteSessionSucceed() throws Exception {
        try (ClubService service = connect()) {
            service.cancelBooking(10);
            service.deleteSession(1);
        }
    }

    // ---------- błędy sieci ----------

    @Test
    void connectingToClosedPortFailsWithIOException() throws Exception {
        int port = fakeServer.getLocalPort();
        fakeServer.close();
        assertThrows(IOException.class, () -> new ServerConnection("localhost", port, 1000));
    }

    @Test
    void serverClosingConnectionGivesIOException() throws Exception {
        ServerSocket oneShot = new ServerSocket(0);
        Thread closer = new Thread(() -> {
            try (Socket s = oneShot.accept()) {
                // od razu zamyka połączenie, nic nie odpowiadając
            } catch (IOException ignored) {
            }
        });
        closer.setDaemon(true);
        closer.start();
        try (ClubService service = new ClubService(new ServerConnection("localhost", oneShot.getLocalPort(), 5000))) {
            assertThrows(IOException.class, service::listMembers);
        } finally {
            oneShot.close();
        }
    }
}

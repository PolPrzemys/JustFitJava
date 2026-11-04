package pl.justfit.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.justfit.common.Actions;
import pl.justfit.common.Json;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;

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

/** Testy klienta na atrapie serwera: nie potrzebują prawdziwego serwera ani bazy. */
@Timeout(30)
class MemberServiceTest {

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
                Request request = Json.fromJson(line, Request.class);
                Response response = switch (request.getAction()) {
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
                    default -> Response.error("Nieznana akcja");
                };
                out.write(Json.toJson(response));
                out.newLine();
                out.flush();
            }
        } catch (IOException ignored) {
            // test zamyka gniazdo
        }
    }

    private MemberService connect() throws IOException {
        return new MemberService(new ServerConnection("localhost", fakeServer.getLocalPort(), 5000));
    }

    @Test
    void loginSucceedsWithGoodPassword() throws Exception {
        try (MemberService service = connect()) {
            service.login("admin", "dobre");
        }
    }

    @Test
    void loginFailureSurfacesServerMessage() throws Exception {
        try (MemberService service = connect()) {
            ServiceException error = assertThrows(ServiceException.class, () -> service.login("admin", "zle"));
            assertEquals("Nieprawidłowy login lub hasło", error.getMessage());
        }
    }

    @Test
    void listMembersParsesTypedList() throws Exception {
        try (MemberService service = connect()) {
            List<Member> members = service.listMembers();
            assertEquals(2, members.size());
            assertEquals("Jan Kowalski", members.get(0).getName());
            assertEquals("Brak karnetu", members.get(1).getStatus());
        }
    }

    @Test
    void addMemberReturnsMemberWithServerAssignedId() throws Exception {
        try (MemberService service = connect()) {
            Member saved = service.addMember(new Member(0, "Ewa", "K-003", null));
            assertEquals(42, saved.getId());
            assertEquals("Ewa", saved.getName());
        }
    }

    @Test
    void errorFromServerBecomesServiceException() throws Exception {
        try (MemberService service = connect()) {
            assertThrows(ServiceException.class, () -> service.deleteMember(7));
        }
    }

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
        try (MemberService service = new MemberService(new ServerConnection("localhost", oneShot.getLocalPort(), 5000))) {
            assertThrows(IOException.class, service::listMembers);
        } finally {
            oneShot.close();
        }
    }
}

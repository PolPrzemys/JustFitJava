package pl.justfit.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.justfit.common.Actions;
import pl.justfit.common.Json;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.server.db.Database;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prawdziwy serwer na losowym porcie i prawdziwe gniazda: sprawdza obsługę wielu klientów naraz. */
@Timeout(60)
class ServerIntegrationTest {

    private JustFitServer server;

    /** Minimalny klient testowy mówiący surowym protokołem. */
    private static class TestClient implements Closeable {
        private final Socket socket;
        private final BufferedReader in;
        private final BufferedWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        Response send(Request request) throws IOException {
            return sendRaw(Json.toJson(request));
        }

        Response sendRaw(String line) throws IOException {
            out.write(line);
            out.newLine();
            out.flush();
            return Json.fromJson(in.readLine(), Response.class);
        }

        Response login() throws IOException {
            return send(Request.of(Actions.LOGIN).with("username", "admin").with("password", "admin123"));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        server = new JustFitServer(0, Database.inMemory("it-" + UUID.randomUUID()));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void manyClientsWorkInParallelAndAllWritesArePersisted() throws Exception {
        int clients = 12;
        int membersPerClient = 5;
        ExecutorService executor = Executors.newFixedThreadPool(clients);
        CountDownLatch allConnected = new CountDownLatch(clients);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            int clientNo = i;
            results.add(executor.submit(() -> {
                try (TestClient client = new TestClient(server.getPort())) {
                    assertTrue(client.login().isOk());
                    // wszyscy czekają, aż wszyscy się połączą, więc zapisy naprawdę idą równocześnie
                    allConnected.countDown();
                    assertTrue(allConnected.await(20, TimeUnit.SECONDS), "nie wszyscy klienci się połączyli");
                    int ok = 0;
                    for (int j = 0; j < membersPerClient; j++) {
                        Member member = new Member(0, "Klient " + clientNo, "N-" + clientNo + "-" + j, null);
                        if (client.send(Request.of(Actions.ADD_MEMBER).with("member", member)).isOk()) {
                            ok++;
                        }
                    }
                    return ok;
                }
            }));
        }

        int totalOk = 0;
        for (Future<Integer> result : results) {
            totalOk += result.get(40, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(clients * membersPerClient, totalOk);
        try (TestClient reader = new TestClient(server.getPort())) {
            reader.login();
            Member[] all = reader.send(Request.of(Actions.LIST_MEMBERS)).data(Member[].class);
            assertEquals(clients * membersPerClient, all.length);
        }
    }

    @Test
    void clientWithoutLoginIsRefusedWhileOthersWork() throws Exception {
        try (TestClient anonymous = new TestClient(server.getPort());
             TestClient staff = new TestClient(server.getPort())) {
            assertTrue(staff.login().isOk());
            assertFalse(anonymous.send(Request.of(Actions.LIST_MEMBERS)).isOk());
            assertTrue(staff.send(Request.of(Actions.LIST_MEMBERS)).isOk());
        }
    }

    @Test
    void malformedJsonGetsErrorAndConnectionStaysUsable() throws Exception {
        try (TestClient client = new TestClient(server.getPort())) {
            Response broken = client.sendRaw("{to nie jest json");
            assertFalse(broken.isOk());
            assertTrue(client.send(Request.of(Actions.PING)).isOk());
        }
    }

    @Test
    void serverTracksConnectedClients() throws Exception {
        assertEquals(0, server.getClientCount());
        try (TestClient client = new TestClient(server.getPort())) {
            client.send(Request.of(Actions.PING));
            assertEquals(1, server.getClientCount());
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (server.getClientCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, server.getClientCount());
    }

    @Test
    void stopDisconnectsClients() throws Exception {
        TestClient client = new TestClient(server.getPort());
        client.send(Request.of(Actions.PING));
        server.stop();
        Response response = null;
        try {
            response = client.send(Request.of(Actions.PING));
        } catch (IOException expected) {
            // oczekiwane: serwer zamknął połączenie
        }
        assertTrue(response == null || !response.isOk());
        client.close();
    }
}

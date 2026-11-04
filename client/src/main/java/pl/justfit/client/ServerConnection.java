package pl.justfit.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.common.Json;
import pl.justfit.common.Request;
import pl.justfit.common.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Połączenie TCP z serwerem. Jedno żądanie naraz: wyślij linię JSON, odbierz linię JSON. */
public class ServerConnection implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(ServerConnection.class);

    private final Socket socket = new Socket();
    private final BufferedReader in;
    private final BufferedWriter out;

    public ServerConnection(String host, int port, int timeoutMillis) throws IOException {
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        log.info("Połączono z {}:{}", host, port);
    }

    /** Synchronizacja chroni przed pomieszaniem odpowiedzi, gdy kilka zadań GUI działa naraz. */
    public synchronized Response send(Request request) throws IOException {
        out.write(Json.toJson(request));
        out.newLine();
        out.flush();
        String line = in.readLine();
        if (line == null) {
            throw new IOException("Serwer zamknął połączenie");
        }
        Response response = Json.fromJson(line, Response.class);
        if (response == null) {
            throw new IOException("Pusta odpowiedź serwera");
        }
        return response;
    }

    @Override
    public void close() {
        try {
            socket.close();
            log.info("Rozłączono z serwerem");
        } catch (IOException e) {
            log.warn("Błąd zamykania połączenia", e);
        }
    }
}

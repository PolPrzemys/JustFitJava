package pl.justfit.server;

import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.common.Json;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.server.db.Database;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Obsługuje jedno połączenie: czyta linie JSON, odpowiada liniami JSON. Działa we własnym wątku. */
public class ClientHandler implements Runnable {

    private static final Logger log = LogManager.getLogger(ClientHandler.class);

    private final Socket socket;
    private final RequestHandler handler;
    private final String remote;
    private final Runnable onClose;

    public ClientHandler(Socket socket, Database database, Runnable onClose) {
        this.socket = socket;
        this.handler = new RequestHandler(database);
        this.remote = String.valueOf(socket.getRemoteSocketAddress());
        this.onClose = onClose;
    }

    public String getRemote() {
        return remote;
    }

    /** Nazwa zalogowanego użytkownika albo null, gdy klient jeszcze się nie zalogował. */
    public String getUsername() {
        return handler.getUsername();
    }

    @Override
    public void run() {
        log.info("Klient połączony: {}", remote);
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Response response = process(line);
                out.write(Json.toJson(response));
                out.newLine();
                out.flush();
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                log.warn("Błąd połączenia z {}: {}", remote, e.getMessage());
            }
        } finally {
            log.info("Klient rozłączony: {}", remote);
            onClose.run();
        }
    }

    private Response process(String line) {
        try {
            Request request = Json.fromJson(line, Request.class);
            return handler.handle(request);
        } catch (JsonSyntaxException e) {
            log.warn("Niepoprawny JSON od {}", remote);
            return Response.error("Niepoprawny format żądania");
        }
    }
}

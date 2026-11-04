package pl.justfit.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.server.db.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Serwer TCP. Jeden wątek przyjmuje połączenia, a każdego klienta obsługuje osobny wątek z puli.
 */
public class JustFitServer {

    private static final Logger log = LogManager.getLogger(JustFitServer.class);

    private final int requestedPort;
    private final Database database;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private ExecutorService pool;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    /** @param port numer portu; 0 oznacza dowolny wolny port (przydatne w testach) */
    public JustFitServer(int port, Database database) {
        this.requestedPort = port;
        this.database = database;
    }

    public synchronized void start() throws IOException {
        if (serverSocket != null) {
            throw new IllegalStateException("Serwer już działa");
        }
        serverSocket = new ServerSocket(requestedPort);
        pool = Executors.newCachedThreadPool();
        acceptThread = new Thread(this::acceptLoop, "accept");
        acceptThread.start();
        log.info("Serwer nasłuchuje na porcie {}", getPort());
    }

    public synchronized int getPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public int getClientCount() {
        return clients.size();
    }

    private void acceptLoop() {
        ServerSocket server = serverSocket;
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                clients.add(socket);
                pool.execute(new ClientHandler(socket, database, () -> clients.remove(socket)));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    log.error("Błąd przyjmowania połączenia", e);
                }
            }
        }
    }

    public synchronized void stop() {
        if (serverSocket == null) {
            return;
        }
        log.info("Zatrzymywanie serwera");
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("Błąd zamykania gniazda serwera", e);
        }
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException ignored) {
                // klient i tak jest rozłączany
            }
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        serverSocket = null;
        log.info("Serwer zatrzymany");
    }
}

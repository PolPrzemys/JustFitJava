package pl.justfit.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.server.db.Database;

/**
 * Uruchomienie serwera z wiersza poleceń: ServerMain [port] [ścieżka-bazy].
 * Okno GUI serwera (start/stop, klienci, logi) dojdzie w kolejnym etapie.
 */
public class ServerMain {

    private static final Logger log = LogManager.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String dbPath = args.length > 1 ? args[1] : "./data/justfit";

        Database database = Database.file(dbPath);
        JustFitServer server = new JustFitServer(port, database);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        server.start();
        log.info("Naciśnij Ctrl+C, aby zatrzymać serwer");
    }
}

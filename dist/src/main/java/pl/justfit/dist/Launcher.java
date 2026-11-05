package pl.justfit.dist;

import javafx.application.Application;
import pl.justfit.client.JustFitApp;
import pl.justfit.server.gui.ServerApp;

import java.util.Arrays;
import java.util.Locale;

/**
 * Punkt wejścia pliku justfit.jar.
 * <ul>
 *   <li>{@code java -jar justfit.jar} (albo dwuklik): okno z wyborem, co uruchomić,</li>
 *   <li>{@code java -jar justfit.jar server}: okno serwera,</li>
 *   <li>{@code java -jar justfit.jar client}: okno klienta.</li>
 * </ul>
 * Ta klasa celowo nie dziedziczy po Application: JavaFX uruchomiony z jednego pliku jar
 * wymaga zwykłej klasy z metodą main.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        String[] rest = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (mode) {
            case "server" -> {
                // nazwa trafia do pliku logów (logs/server.log), patrz log4j2.xml w tym module
                System.setProperty("justfit.app", "server");
                ServerApp.main(rest);
            }
            case "client" -> {
                System.setProperty("justfit.app", "client");
                JustFitApp.main(rest);
            }
            default -> Application.launch(ChooserApp.class, args);
        }
    }
}

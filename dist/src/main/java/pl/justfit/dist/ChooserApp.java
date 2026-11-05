package pl.justfit.dist;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

/**
 * Małe okno startowe z dwoma przyciskami. Serwer i klient uruchamiają się jako osobne procesy
 * (tak jak na dwóch różnych komputerach), więc zamknięcie tego okna ich nie wyłącza.
 */
public class ChooserApp extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("JustFit");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Wybierz, co uruchomić");
        subtitle.getStyleClass().add("muted");

        Button server = new Button("Uruchom serwer");
        server.getStyleClass().addAll("primary", "big");
        server.setMaxWidth(Double.MAX_VALUE);
        Button client = new Button("Uruchom klienta");
        client.getStyleClass().add("big");
        client.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Najpierw uruchom serwer i kliknij w jego oknie „Start”, potem uruchom klienta. "
                + "Klientów może być kilku naraz.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        Label status = new Label();
        status.getStyleClass().add("error-text");
        status.setWrapText(true);

        server.setOnAction(e -> status.setText(spawn("server")));
        client.setOnAction(e -> status.setText(spawn("client")));

        VBox card = new VBox(16, title, subtitle, server, client, hint, status);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(380);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 460, 500);
        URL stylesheet = getClass().getResource("/justfit.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("JustFit");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Uruchamia ten sam plik jar jeszcze raz, z argumentem server albo client.
     *
     * @return pusty tekst, gdy się udało, albo opis problemu do pokazania w oknie
     */
    private String spawn(String mode) {
        try {
            File jar = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.getName().endsWith(".jar")) {
                return "Uruchamianie z okna działa tylko z pliku justfit.jar. "
                        + "Z IDE użyj: java -jar justfit.jar " + mode;
            }
            String java = ProcessHandle.current().info().command().orElse("java");
            File logs = new File("logs");
            logs.mkdirs();

            // komunikaty konsoli (np. ostrzeżenia JavaFX albo przyczyna awarii przy starcie) idą do pliku
            ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.getPath(), mode);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(logs, mode + "-console.log")));
            builder.start();
            return "";
        } catch (Exception e) {
            return "Nie udało się uruchomić: " + e.getMessage();
        }
    }
}

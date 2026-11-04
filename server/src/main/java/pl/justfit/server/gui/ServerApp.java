package pl.justfit.server.gui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.server.ClientInfo;
import pl.justfit.server.JustFitServer;
import pl.justfit.server.db.Database;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Okno serwera: start i stop, lista połączonych klientów (odświeżana co sekundę)
 * oraz podgląd logów na żywo.
 */
public class ServerApp extends Application {

    private static final Logger log = LogManager.getLogger(ServerApp.class);
    private static final int MAX_LOG_CHARS = 100_000;

    private Database database;
    private JustFitServer server;
    private Timeline refresh;
    private Consumer<String> logListener;

    private final ObservableList<ClientInfo> clients = FXCollections.observableArrayList();
    private final TextField portField = new TextField("5000");
    private final Button startButton = new Button("Start");
    private final Button stopButton = new Button("Stop");
    private final Label statusLabel = new Label("Serwer zatrzymany");
    private final Label countLabel = new Label("Połączeni klienci: 0");
    private final TextArea logArea = new TextArea();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        GuiLogAppender.install();
        logListener = line -> Platform.runLater(() -> appendLog(line));
        GuiLogAppender.addListener(logListener);

        database = Database.file("./data/justfit");

        portField.setPrefColumnCount(5);
        stopButton.setDisable(true);
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, new Label("Port:"), portField, startButton, stopButton, spacer, statusLabel);
        toolbar.setPadding(new Insets(10));

        TableView<ClientInfo> table = new TableView<>(clients);
        table.getColumns().add(column("Adres klienta", 260, ClientInfo::address));
        table.getColumns().add(column("Użytkownik", 160,
                info -> info.username() == null ? "(niezalogowany)" : info.username()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Brak połączonych klientów"));

        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle("-fx-font-family: 'monospace';");

        SplitPane center = new SplitPane(table, logArea);
        center.setOrientation(Orientation.VERTICAL);
        center.setDividerPositions(0.35);

        HBox bottom = new HBox(countLabel);
        bottom.setPadding(new Insets(6, 10, 6, 10));

        BorderPane root = new BorderPane(center);
        root.setTop(toolbar);
        root.setBottom(bottom);

        refresh = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshClients()));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();

        stage.setTitle("JustFit — serwer");
        stage.setScene(new Scene(root, 760, 520));
        stage.show();
    }

    @Override
    public void stop() {
        if (refresh != null) {
            refresh.stop();
        }
        if (server != null) {
            server.stop();
        }
        if (logListener != null) {
            GuiLogAppender.removeListener(logListener);
        }
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showError("Port musi być liczbą z zakresu 0-65535.");
            return;
        }
        JustFitServer candidate = new JustFitServer(port, database);
        try {
            candidate.start();
        } catch (IOException e) {
            log.error("Nie udało się uruchomić serwera na porcie {}", port, e);
            showError("Nie można uruchomić serwera na porcie " + port + ": " + e.getMessage());
            return;
        }
        server = candidate;
        startButton.setDisable(true);
        stopButton.setDisable(false);
        portField.setDisable(true);
        statusLabel.setText("Serwer działa na porcie " + server.getPort());
    }

    /** Zatrzymanie czeka na zakończenie wątków klientów, więc robimy je poza wątkiem GUI. */
    private void stopServer() {
        JustFitServer running = server;
        if (running == null) {
            return;
        }
        stopButton.setDisable(true);
        Thread thread = new Thread(() -> {
            running.stop();
            Platform.runLater(this::onStopped);
        }, "server-stop");
        thread.setDaemon(true);
        thread.start();
    }

    private void onStopped() {
        server = null;
        clients.clear();
        countLabel.setText("Połączeni klienci: 0");
        statusLabel.setText("Serwer zatrzymany");
        startButton.setDisable(false);
        portField.setDisable(false);
    }

    private void refreshClients() {
        JustFitServer running = server;
        if (running == null || !running.isRunning()) {
            return;
        }
        clients.setAll(running.getClients());
        countLabel.setText("Połączeni klienci: " + clients.size());
    }

    private void appendLog(String line) {
        if (logArea.getLength() > MAX_LOG_CHARS) {
            logArea.deleteText(0, MAX_LOG_CHARS / 2);
        }
        logArea.appendText(line + System.lineSeparator());
    }

    private static TableColumn<ClientInfo, String> column(String title, int width,
                                                           java.util.function.Function<ClientInfo, String> value) {
        TableColumn<ClientInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}

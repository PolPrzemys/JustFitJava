package pl.justfit.client;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Okno klienta: logowanie, a po nim zakładki Członkowie, Zajęcia i Trenerzy. */
public class JustFitApp extends Application {

    private static final Logger log = LogManager.getLogger(JustFitApp.class);
    private static final int TIMEOUT_MS = 10_000;

    private Stage stage;
    private ClubService service;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("JustFit");
        // gdy serwer przestanie odpowiadać, wracamy do ekranu logowania
        Ui.setConnectionLostHandler(this::showLogin);
        showLogin();
        stage.show();
    }

    @Override
    public void stop() {
        if (service != null) {
            service.close();
        }
    }

    // ---------- logowanie ----------

    private void showLogin() {
        if (service != null) {
            service.close();
            service = null;
        }
        TextField host = new TextField("localhost");
        TextField port = new TextField("5000");
        TextField user = new TextField("admin");
        PasswordField password = new PasswordField();
        Label status = new Label();
        status.setStyle("-fx-text-fill: #b00020;");
        Button login = new Button("Zaloguj");
        login.setDefaultButton(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Serwer"), host);
        form.addRow(1, new Label("Port"), port);
        form.addRow(2, new Label("Login"), user);
        form.addRow(3, new Label("Hasło"), password);

        login.setOnAction(e -> {
            int portNumber;
            try {
                portNumber = Integer.parseInt(port.getText().trim());
            } catch (NumberFormatException ex) {
                status.setText("Port musi być liczbą");
                return;
            }
            login.setDisable(true);
            status.setText("Łączenie...");
            Task<ClubService> task = new Task<>() {
                @Override
                protected ClubService call() throws Exception {
                    ServerConnection connection = new ServerConnection(host.getText().trim(), portNumber, TIMEOUT_MS);
                    ClubService s = new ClubService(connection);
                    try {
                        s.login(user.getText().trim(), password.getText());
                    } catch (Exception ex) {
                        s.close();
                        throw ex;
                    }
                    return s;
                }
            };
            task.setOnSucceeded(ev -> {
                service = task.getValue();
                showMain();
            });
            task.setOnFailed(ev -> {
                log.warn("Logowanie nieudane: {}", task.getException().getMessage());
                status.setText(task.getException() instanceof ServiceException
                        ? task.getException().getMessage()
                        : "Błąd połączenia z serwerem: " + task.getException().getMessage());
                login.setDisable(false);
            });
            Thread thread = new Thread(task, "network");
            thread.setDaemon(true);
            thread.start();
        });

        VBox root = new VBox(15, new Label("JustFit — logowanie"), form, login, status);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.CENTER_LEFT);
        stage.setScene(new Scene(root, 360, 330));
    }

    // ---------- główne okno z zakładkami ----------

    private void showMain() {
        MembersTab members = new MembersTab(service);
        SessionsTab sessions = new SessionsTab(service);
        TrainersTab trainers = new TrainersTab(service);

        TabPane tabs = new TabPane(members, sessions, trainers);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // przy wejściu na zakładkę pobieramy jej dane od nowa
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old, tab) -> {
            if (tab instanceof Refreshable refreshable) {
                refreshable.refresh();
            }
        });

        stage.setScene(new Scene(tabs, 950, 620));
        stage.centerOnScreen();
        members.refresh();
    }
}

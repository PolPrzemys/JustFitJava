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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
        status.getStyleClass().add("error-text");
        status.setWrapText(true);
        Button login = new Button("Zaloguj");
        login.getStyleClass().addAll("primary", "big");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("JustFit");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Panel recepcji klubu fitness");
        subtitle.getStyleClass().add("muted");

        VBox form = new VBox(6,
                fieldLabel("Serwer"), host,
                fieldLabel("Port"), port,
                fieldLabel("Login"), user,
                fieldLabel("Hasło"), password);

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

        VBox card = new VBox(16, title, subtitle, form, login, status);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(380);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 520, 640);
        Ui.style(scene);
        stage.setScene(scene);
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        VBox.setMargin(label, new Insets(6, 0, 0, 0));
        return label;
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

        Label title = new Label("JustFit");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Panel recepcji");
        subtitle.getStyleClass().add("app-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button logout = new Button("Wyloguj");
        logout.setOnAction(e -> showLogin());
        HBox header = new HBox(14, new VBox(0, title, subtitle), spacer, logout);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");

        BorderPane root = new BorderPane(tabs);
        root.setTop(header);

        Scene scene = new Scene(root, 1040, 700);
        Ui.style(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        members.refresh();
    }
}

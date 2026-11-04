package pl.justfit.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.justfit.common.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Okno klienta: logowanie, a po nim lista członków z dodawaniem, edycją i usuwaniem. */
public class JustFitApp extends Application {

    private static final Logger log = LogManager.getLogger(JustFitApp.class);
    private static final int TIMEOUT_MS = 10_000;

    private Stage stage;
    private MemberService service;
    private final ObservableList<Member> members = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("JustFit");
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
            Task<MemberService> task = new Task<>() {
                @Override
                protected MemberService call() throws Exception {
                    ServerConnection connection = new ServerConnection(host.getText().trim(), portNumber, TIMEOUT_MS);
                    MemberService s = new MemberService(connection);
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
                status.setText(describe(task.getException()));
                login.setDisable(false);
            });
            startDaemon(task);
        });

        VBox root = new VBox(15, new Label("JustFit — logowanie"), form, login, status);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.CENTER_LEFT);
        stage.setScene(new Scene(root, 360, 330));
    }

    // ---------- lista członków ----------

    private void showMain() {
        TableView<Member> table = new TableView<>(members);
        table.getColumns().add(column("Nr karnetu", "membershipNumber", 130));
        table.getColumns().add(column("Imię i nazwisko", "name", 260));
        table.getColumns().add(column("Ważny do", "expirationDate", 120));
        table.getColumns().add(column("Status", "status", 110));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj");
        Button edit = new Button("Edytuj");
        Button delete = new Button("Usuń");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        refresh.setOnAction(e -> refresh());
        add.setOnAction(e -> memberDialog(null).ifPresent(m ->
                run(() -> service.addMember(m), saved -> refresh())));
        edit.setOnAction(e -> memberDialog(table.getSelectionModel().getSelectedItem()).ifPresent(m ->
                run(() -> service.updateMember(m), saved -> refresh())));
        delete.setOnAction(e -> {
            Member selected = table.getSelectionModel().getSelectedItem();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Usunąć członka " + selected.getName() + "?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b ->
                    run(() -> {
                        service.deleteMember(selected.getId());
                        return null;
                    }, ok -> refresh()));
        });

        HBox toolbar = new HBox(10, refresh, add, edit, delete);
        toolbar.setPadding(new Insets(10));

        BorderPane root = new BorderPane(table);
        root.setTop(toolbar);
        stage.setScene(new Scene(root, 700, 450));
        refresh();
    }

    private void refresh() {
        run(() -> service.listMembers(), (List<Member> list) -> members.setAll(list));
    }

    private Optional<Member> memberDialog(Member existing) {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nowy członek" : "Edycja członka");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField number = new TextField(existing == null ? "" : existing.getMembershipNumber());
        TextField name = new TextField(existing == null ? "" : existing.getName());
        DatePicker expiration = new DatePicker();
        if (existing != null && existing.getExpirationDate() != null && !existing.getExpirationDate().isBlank()) {
            expiration.setValue(LocalDate.parse(existing.getExpirationDate()));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Nr karnetu"), number);
        grid.addRow(1, new Label("Imię i nazwisko"), name);
        grid.addRow(2, new Label("Ważny do"), expiration);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            LocalDate date = expiration.getValue();
            return new Member(existing == null ? 0 : existing.getId(),
                    name.getText().trim(), number.getText().trim(), date == null ? null : date.toString());
        });
        return dialog.showAndWait();
    }

    // ---------- pomocnicze ----------

    private TableColumn<Member, String> column(String title, String property, int width) {
        TableColumn<Member, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    /** Wykonuje wywołanie sieciowe poza wątkiem GUI i wynik oddaje z powrotem do GUI. */
    private <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            log.warn("Operacja nieudana: {}", task.getException().getMessage());
            new Alert(Alert.AlertType.ERROR, describe(task.getException())).showAndWait();
            if (task.getException() instanceof java.io.IOException) {
                Platform.runLater(this::showLogin);
            }
        });
        startDaemon(task);
    }

    private static String describe(Throwable error) {
        if (error instanceof ServiceException) {
            return error.getMessage();
        }
        return "Błąd połączenia z serwerem: " + error.getMessage();
    }

    private static void startDaemon(Task<?> task) {
        Thread thread = new Thread(task, "network");
        thread.setDaemon(true);
        thread.start();
    }
}

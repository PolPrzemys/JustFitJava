package pl.justfit.client;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Member;
import pl.justfit.common.Trainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Zakładka „Zajęcia”: u góry grafik zajęć, pod spodem uczestnicy zaznaczonych zajęć.
 * Recepcja zapisuje członka na zajęcia albo anuluje jego zapis.
 */
public class SessionsTab extends Tab implements Refreshable {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d.MM, HH:mm", Locale.forLanguageTag("pl"));

    private final ClubService service;
    private final ObservableList<ClassSession> sessions = FXCollections.observableArrayList();
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();
    private final TableView<ClassSession> sessionTable = new TableView<>(sessions);
    private final TableView<Booking> bookingTable = new TableView<>(bookings);
    private final Label detailTitle = new Label();

    public SessionsTab(ClubService service) {
        super("Zajęcia");
        this.service = service;
        setClosable(false);

        // --- grafik zajęć ---
        sessionTable.getColumns().add(Ui.column("Termin", 150, SessionsTab::when));
        sessionTable.getColumns().add(Ui.column("Zajęcia", 200, ClassSession::getTitle));
        sessionTable.getColumns().add(Ui.column("Trener", 170, ClassSession::getTrainerName));
        sessionTable.getColumns().add(Ui.column("Sala", 100, ClassSession::getRoom));
        sessionTable.getColumns().add(Ui.column("Miejsca", 90,
                s -> s.getBooked() + " / " + s.getCapacity()));
        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionTable.setPlaceholder(new Label("Brak zajęć. Dodaj pierwsze przyciskiem „Dodaj zajęcia”."));

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj zajęcia");
        Button delete = new Button("Usuń zajęcia");
        delete.disableProperty().bind(sessionTable.getSelectionModel().selectedItemProperty().isNull());
        refresh.setOnAction(e -> reload());
        add.setOnAction(e -> addSession());
        delete.setOnAction(e -> deleteSession());

        HBox toolbar = new HBox(10, refresh, add, delete);
        toolbar.setPadding(new Insets(10));

        // --- uczestnicy zaznaczonych zajęć ---
        bookingTable.getColumns().add(Ui.column("Nr karnetu", 130, Booking::getMembershipNumber));
        bookingTable.getColumns().add(Ui.column("Imię i nazwisko", 300, Booking::getMemberName));
        bookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bookingTable.setPlaceholder(new Label("Brak zapisanych osób"));

        Button book = new Button("Zapisz członka");
        Button cancel = new Button("Anuluj zapis");
        book.disableProperty().bind(sessionTable.getSelectionModel().selectedItemProperty().isNull());
        cancel.disableProperty().bind(bookingTable.getSelectionModel().selectedItemProperty().isNull());
        book.setOnAction(e -> bookMember());
        cancel.setOnAction(e -> cancelBooking());

        detailTitle.setText("Zaznacz zajęcia, aby zobaczyć uczestników");
        HBox detailBar = new HBox(10, detailTitle, book, cancel);
        detailBar.setPadding(new Insets(10));
        VBox detail = new VBox(detailBar, bookingTable);
        VBox.setVgrow(bookingTable, javafx.scene.layout.Priority.ALWAYS);

        sessionTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, old, selected) -> showBookings(selected));

        SplitPane split = new SplitPane(sessionTable, detail);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);

        BorderPane content = new BorderPane(split);
        content.setTop(toolbar);
        setContent(content);
    }

    @Override
    public void refresh() {
        reload();
    }

    /** Pobiera grafik od nowa i zachowuje zaznaczenie, żeby po zapisie nie trzeba było klikać ponownie. */
    private void reload() {
        ClassSession selected = sessionTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1 : selected.getId();
        Ui.run(() -> service.listSessions(), list -> {
            sessions.setAll(list);
            list.stream().filter(s -> s.getId() == selectedId).findFirst()
                    .ifPresent(s -> sessionTable.getSelectionModel().select(s));
        });
    }

    private void showBookings(ClassSession session) {
        if (session == null) {
            bookings.clear();
            detailTitle.setText("Zaznacz zajęcia, aby zobaczyć uczestników");
            return;
        }
        detailTitle.setText(session.getTitle() + " — " + session.getTrainerName()
                + ", zapisanych " + session.getBooked() + " z " + session.getCapacity());
        Ui.run(() -> service.listBookings(session.getId()), list -> {
            // odpowiedź mogła dojść po zmianie zaznaczenia: wtedy ją pomijamy
            ClassSession current = sessionTable.getSelectionModel().getSelectedItem();
            if (current != null && current.getId() == session.getId()) {
                bookings.setAll(list);
            }
        });
    }

    // ---------- dodawanie i usuwanie zajęć ----------

    private void addSession() {
        Ui.run(() -> service.listTrainers(), trainers -> {
            if (trainers.isEmpty()) {
                Ui.info("Najpierw dodaj trenera w zakładce „Trenerzy”.");
                return;
            }
            sessionDialog(trainers).ifPresent(session ->
                    Ui.run(() -> service.addSession(session), saved -> reload()));
        });
    }

    private void deleteSession() {
        ClassSession selected = sessionTable.getSelectionModel().getSelectedItem();
        if (Ui.confirm("Usunąć zajęcia „" + selected.getTitle() + "”? Zostaną też usunięte wszystkie zapisy.")) {
            Ui.runVoid(() -> service.deleteSession(selected.getId()), this::reload);
        }
    }

    /** Okno nowych zajęć. Proste braki w formularzu są wyłapywane tu, reszty pilnuje serwer. */
    private Optional<ClassSession> sessionDialog(List<Trainer> trainers) {
        Dialog<ClassSession> dialog = new Dialog<>();
        dialog.setTitle("Nowe zajęcia");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField title = new TextField();
        ComboBox<Trainer> trainer = new ComboBox<>(FXCollections.observableArrayList(trainers));
        trainer.getSelectionModel().selectFirst();
        TextField room = new TextField();
        DatePicker date = new DatePicker(LocalDate.now().plusDays(1));
        ComboBox<LocalTime> time = new ComboBox<>();
        for (LocalTime t = LocalTime.of(6, 0); !t.isAfter(LocalTime.of(22, 0)); t = t.plusMinutes(15)) {
            time.getItems().add(t);
        }
        time.setValue(LocalTime.of(18, 0));
        Spinner<Integer> capacity = new Spinner<>(1, 500, 12);
        Label error = new Label();
        error.setStyle("-fx-text-fill: #b00020;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Nazwa zajęć"), title);
        grid.addRow(1, new Label("Trener"), trainer);
        grid.addRow(2, new Label("Sala"), room);
        grid.addRow(3, new Label("Data"), date);
        grid.addRow(4, new Label("Godzina"), time);
        grid.addRow(5, new Label("Limit miejsc"), capacity);
        grid.add(error, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);

        // okno zostaje otwarte, dopóki formularz ma braki
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            String problem = null;
            if (title.getText().isBlank()) {
                problem = "Podaj nazwę zajęć";
            } else if (room.getText().isBlank()) {
                problem = "Podaj salę";
            } else if (date.getValue() == null) {
                problem = "Wybierz datę";
            } else if (LocalDateTime.of(date.getValue(), time.getValue()).isBefore(LocalDateTime.now())) {
                problem = "Termin nie może być w przeszłości";
            }
            if (problem != null) {
                error.setText(problem);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String start = LocalDateTime.of(date.getValue(), time.getValue()).toString();
            return new ClassSession(0, title.getText().trim(), trainer.getValue().getId(), null,
                    room.getText().trim(), start, capacity.getValue(), 0);
        });
        return dialog.showAndWait();
    }

    // ---------- zapisy ----------

    private void bookMember() {
        ClassSession session = sessionTable.getSelectionModel().getSelectedItem();
        Ui.run(() -> service.listMembers(), members -> {
            if (members.isEmpty()) {
                Ui.info("Brak członków. Dodaj członka w zakładce „Członkowie”.");
                return;
            }
            ChoiceDialog<Member> dialog = new ChoiceDialog<>(members.get(0), members);
            dialog.setTitle("Zapis na zajęcia");
            dialog.setHeaderText(session.getTitle() + ", " + when(session));
            dialog.setContentText("Członek:");
            dialog.showAndWait().ifPresent(member ->
                    Ui.run(() -> service.book(session.getId(), member.getId()), booking -> reload()));
        });
    }

    private void cancelBooking() {
        Booking selected = bookingTable.getSelectionModel().getSelectedItem();
        if (Ui.confirm("Anulować zapis: " + selected.getMemberName() + "?")) {
            Ui.runVoid(() -> service.cancelBooking(selected.getId()), this::reload);
        }
    }

    private static String when(ClassSession session) {
        try {
            return session.startAsDateTime().format(WHEN);
        } catch (DateTimeParseException e) {
            return session.getStartTime();
        }
    }
}

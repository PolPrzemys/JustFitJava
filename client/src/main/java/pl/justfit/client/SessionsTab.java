package pl.justfit.client;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    /** Od tego zapełnienia pasek robi się pomarańczowy („ostatnie miejsca”). */
    private static final double ALMOST_FULL = 0.8;

    private final ClubService service;
    private final ObservableList<ClassSession> sessions = FXCollections.observableArrayList();
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();
    private final TableView<ClassSession> sessionTable = new TableView<>(sessions);
    private final TableView<Booking> bookingTable = new TableView<>(bookings);
    private final Label detailTitle = new Label();
    private final StatTile sessionsTile = new StatTile("Zajęcia w grafiku", "neutral");
    private final StatTile bookedTile = new StatTile("Zapisanych osób", "accent");
    private final StatTile fullTile = new StatTile("Pełne zajęcia", "bad");

    public SessionsTab(ClubService service) {
        super("Zajęcia");
        this.service = service;
        setClosable(false);

        // --- grafik zajęć ---
        sessionTable.getColumns().add(Ui.column("Termin", 150, SessionsTab::when));
        sessionTable.getColumns().add(Ui.column("Zajęcia", 200, ClassSession::getTitle));
        sessionTable.getColumns().add(Ui.column("Trener", 170, ClassSession::getTrainerName));
        sessionTable.getColumns().add(Ui.column("Sala", 100, ClassSession::getRoom));
        sessionTable.getColumns().add(spotsColumn());
        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionTable.setPlaceholder(new Label("Brak zajęć. Dodaj pierwsze przyciskiem „Dodaj zajęcia”."));

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj zajęcia");
        add.getStyleClass().add("primary");
        Button delete = new Button("Usuń zajęcia");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(sessionTable.getSelectionModel().selectedItemProperty().isNull());
        refresh.setOnAction(e -> reload());
        add.setOnAction(e -> addSession());
        delete.setOnAction(e -> deleteSession());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, add, delete, spacer, refresh);
        HBox tiles = new HBox(14, sessionsTile, bookedTile, fullTile);

        VBox.setVgrow(sessionTable, Priority.ALWAYS);
        VBox top = new VBox(14, tiles, toolbar, sessionTable);
        top.setPadding(new Insets(18, 18, 8, 18));

        // --- uczestnicy zaznaczonych zajęć ---
        bookingTable.getColumns().add(Ui.column("Nr karnetu", 130, Booking::getMembershipNumber));
        bookingTable.getColumns().add(Ui.column("Imię i nazwisko", 300, Booking::getMemberName));
        bookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bookingTable.setPlaceholder(new Label("Brak zapisanych osób"));

        Button book = new Button("Zapisz członka");
        book.getStyleClass().add("primary");
        Button cancel = new Button("Anuluj zapis");
        cancel.getStyleClass().add("danger");
        book.disableProperty().bind(sessionTable.getSelectionModel().selectedItemProperty().isNull());
        cancel.disableProperty().bind(bookingTable.getSelectionModel().selectedItemProperty().isNull());
        book.setOnAction(e -> bookMember());
        cancel.setOnAction(e -> cancelBooking());

        detailTitle.getStyleClass().add("section-title");
        detailTitle.setText("Zaznacz zajęcia, aby zobaczyć uczestników");
        Region detailSpacer = new Region();
        HBox.setHgrow(detailSpacer, Priority.ALWAYS);
        HBox detailBar = new HBox(10, detailTitle, detailSpacer, book, cancel);
        detailBar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(bookingTable, Priority.ALWAYS);
        VBox detail = new VBox(12, detailBar, bookingTable);
        detail.setPadding(new Insets(8, 18, 18, 18));

        sessionTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, old, selected) -> showBookings(selected));

        SplitPane split = new SplitPane(top, detail);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);

        setContent(new BorderPane(split));
    }

    @Override
    public void refresh() {
        reload();
    }

    /** Kolumna „Miejsca”: liczba zapisanych i pasek zapełnienia (zielony, pomarańczowy, czerwony gdy pełne). */
    private TableColumn<ClassSession, ClassSession> spotsColumn() {
        TableColumn<ClassSession, ClassSession> column = new TableColumn<>("Miejsca");
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label label = new Label();
            private final HBox box = new HBox(10, bar, label);

            {
                bar.setPrefWidth(80);
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(ClassSession session, boolean empty) {
                super.updateItem(session, empty);
                if (empty || session == null) {
                    setGraphic(null);
                    return;
                }
                double fill = session.getCapacity() == 0 ? 0 : (double) session.getBooked() / session.getCapacity();
                bar.setProgress(fill);
                bar.getStyleClass().removeAll("bar-ok", "bar-warn", "bar-full");
                bar.getStyleClass().add(session.isFull() ? "bar-full" : fill >= ALMOST_FULL ? "bar-warn" : "bar-ok");
                label.setText(session.getBooked() + " / " + session.getCapacity() + (session.isFull() ? "  pełne" : ""));
                setGraphic(box);
            }
        });
        column.setPrefWidth(190);
        return column;
    }

    /** Pobiera grafik od nowa i zachowuje zaznaczenie, żeby po zapisie nie trzeba było klikać ponownie. */
    private void reload() {
        ClassSession selected = sessionTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1 : selected.getId();
        Ui.run(() -> service.listSessions(), list -> {
            sessions.setAll(list);
            updateTiles(list);
            list.stream().filter(s -> s.getId() == selectedId).findFirst()
                    .ifPresent(s -> sessionTable.getSelectionModel().select(s));
        });
    }

    private void updateTiles(List<ClassSession> list) {
        sessionsTile.setValue(list.size());
        bookedTile.setValue(list.stream().mapToInt(ClassSession::getBooked).sum());
        fullTile.setValue(list.stream().filter(ClassSession::isFull).count());
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
        Ui.style(dialog);

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
        error.getStyleClass().add("error-text");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
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
        ok.getStyleClass().add("primary");
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
            Ui.style(dialog);
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

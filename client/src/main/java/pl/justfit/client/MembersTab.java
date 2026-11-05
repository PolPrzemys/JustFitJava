package pl.justfit.client;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import pl.justfit.common.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Zakładka „Członkowie”: kafelki ze statystykami, lista z dodawaniem, edycją i usuwaniem oraz status karnetu. */
public class MembersTab extends Tab implements Refreshable {

    private static final String ACTIVE = "Aktywny";
    private static final String EXPIRED = "Wygasł";
    private static final String NO_MEMBERSHIP = "Brak karnetu";

    private final ClubService service;
    private final ObservableList<Member> members = FXCollections.observableArrayList();
    private final TableView<Member> table = new TableView<>(members);
    private final StatTile totalTile = new StatTile("Wszyscy członkowie", "neutral");
    private final StatTile activeTile = new StatTile("Aktywne karnety", "good");
    private final StatTile expiredTile = new StatTile("Wygasłe karnety", "bad");
    private final StatTile noneTile = new StatTile("Bez karnetu", "accent");

    public MembersTab(ClubService service) {
        super("Członkowie");
        this.service = service;
        setClosable(false);

        table.getColumns().add(Ui.column("Nr karnetu", 130, Member::getMembershipNumber));
        table.getColumns().add(Ui.column("Imię i nazwisko", 260, Member::getName));
        table.getColumns().add(Ui.column("Ważny do", 120, Member::getExpirationDate));
        table.getColumns().add(Ui.badgeColumn("Status", 130, Member::getStatus, MembersTab::badgeKind));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Brak członków. Dodaj pierwszego przyciskiem „Dodaj”."));

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj członka");
        add.getStyleClass().add("primary");
        Button edit = new Button("Edytuj");
        Button delete = new Button("Usuń");
        delete.getStyleClass().add("danger");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        refresh.setOnAction(e -> refresh());
        add.setOnAction(e -> dialog(null).ifPresent(m ->
                Ui.run(() -> service.addMember(m), saved -> refresh())));
        edit.setOnAction(e -> dialog(table.getSelectionModel().getSelectedItem()).ifPresent(m ->
                Ui.run(() -> service.updateMember(m), saved -> refresh())));
        delete.setOnAction(e -> {
            Member selected = table.getSelectionModel().getSelectedItem();
            if (Ui.confirm("Usunąć członka " + selected.getName() + "? Zostaną też usunięte jego zapisy na zajęcia.")) {
                Ui.runVoid(() -> service.deleteMember(selected.getId()), this::refresh);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, add, edit, delete, spacer, refresh);

        HBox tiles = new HBox(14, totalTile, activeTile, expiredTile, noneTile);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox content = new VBox(14, tiles, toolbar, table);
        content.setPadding(new Insets(18));
        setContent(new BorderPane(content));
    }

    @Override
    public void refresh() {
        Ui.run(() -> service.listMembers(), list -> {
            members.setAll(list);
            updateTiles(list);
        });
    }

    private void updateTiles(List<Member> list) {
        totalTile.setValue(list.size());
        activeTile.setValue(count(list, ACTIVE));
        expiredTile.setValue(count(list, EXPIRED));
        noneTile.setValue(count(list, NO_MEMBERSHIP));
    }

    private static long count(List<Member> list, String status) {
        return list.stream().filter(m -> status.equals(m.getStatus())).count();
    }

    private static String badgeKind(String status) {
        return switch (status) {
            case ACTIVE -> "good";
            case EXPIRED -> "bad";
            case NO_MEMBERSHIP -> "muted";
            default -> "warn";
        };
    }

    /** Okno dodawania (existing == null) albo edycji członka. */
    private Optional<Member> dialog(Member existing) {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nowy członek" : "Edycja członka");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.style(dialog);

        TextField number = new TextField(existing == null ? "" : existing.getMembershipNumber());
        TextField name = new TextField(existing == null ? "" : existing.getName());
        DatePicker expiration = new DatePicker();
        if (existing != null && existing.getExpirationDate() != null && !existing.getExpirationDate().isBlank()) {
            expiration.setValue(LocalDate.parse(existing.getExpirationDate()));
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
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
}

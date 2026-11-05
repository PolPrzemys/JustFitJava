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
import pl.justfit.common.Member;

import java.time.LocalDate;
import java.util.Optional;

/** Zakładka „Członkowie”: lista z dodawaniem, edycją i usuwaniem oraz datą ważności karnetu. */
public class MembersTab extends Tab implements Refreshable {

    private final ClubService service;
    private final ObservableList<Member> members = FXCollections.observableArrayList();
    private final TableView<Member> table = new TableView<>(members);

    public MembersTab(ClubService service) {
        super("Członkowie");
        this.service = service;
        setClosable(false);

        table.getColumns().add(Ui.column("Nr karnetu", 130, Member::getMembershipNumber));
        table.getColumns().add(Ui.column("Imię i nazwisko", 260, Member::getName));
        table.getColumns().add(Ui.column("Ważny do", 120, Member::getExpirationDate));
        table.getColumns().add(Ui.column("Status", 110, Member::getStatus));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Brak członków. Dodaj pierwszego przyciskiem „Dodaj”."));

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj");
        Button edit = new Button("Edytuj");
        Button delete = new Button("Usuń");
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

        HBox toolbar = new HBox(10, refresh, add, edit, delete);
        toolbar.setPadding(new Insets(10));

        BorderPane content = new BorderPane(table);
        content.setTop(toolbar);
        setContent(content);
    }

    @Override
    public void refresh() {
        Ui.run(() -> service.listMembers(), list -> members.setAll(list));
    }

    /** Okno dodawania (existing == null) albo edycji członka. */
    private Optional<Member> dialog(Member existing) {
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
}

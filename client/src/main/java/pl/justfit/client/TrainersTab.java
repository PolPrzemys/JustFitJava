package pl.justfit.client;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import pl.justfit.common.Trainer;

/** Zakładka „Trenerzy”: lista trenerów, dodawanie i usuwanie (trenera z zajęciami nie da się usunąć). */
public class TrainersTab extends Tab implements Refreshable {

    private final ClubService service;
    private final ObservableList<Trainer> trainers = FXCollections.observableArrayList();
    private final TableView<Trainer> table = new TableView<>(trainers);

    public TrainersTab(ClubService service) {
        super("Trenerzy");
        this.service = service;
        setClosable(false);

        table.getColumns().add(Ui.column("Imię i nazwisko", 400, Trainer::getName));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Brak trenerów. Dodaj pierwszego, aby móc tworzyć zajęcia."));

        Button refresh = new Button("Odśwież");
        Button add = new Button("Dodaj trenera");
        add.getStyleClass().add("primary");
        Button delete = new Button("Usuń");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        refresh.setOnAction(e -> refresh());
        add.setOnAction(e -> addTrainer());
        delete.setOnAction(e -> {
            Trainer selected = table.getSelectionModel().getSelectedItem();
            if (Ui.confirm("Usunąć trenera " + selected.getName() + "?")) {
                Ui.runVoid(() -> service.deleteTrainer(selected.getId()), this::refresh);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, add, delete, spacer, refresh);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox content = new VBox(14, toolbar, table);
        content.setPadding(new Insets(18));
        setContent(new BorderPane(content));
    }

    @Override
    public void refresh() {
        Ui.run(() -> service.listTrainers(), list -> trainers.setAll(list));
    }

    private void addTrainer() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nowy trener");
        dialog.setHeaderText(null);
        dialog.setContentText("Imię i nazwisko:");
        Ui.style(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name ->
                Ui.run(() -> service.addTrainer(new Trainer(0, name)), saved -> refresh()));
    }
}

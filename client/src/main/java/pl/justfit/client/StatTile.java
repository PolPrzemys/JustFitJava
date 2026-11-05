package pl.justfit.client;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Mały kafelek ze statystyką: duża liczba i podpis pod nią (np. „Aktywne karnety”). */
public class StatTile extends VBox {

    private final Label value = new Label("0");

    /**
     * @param caption podpis pod liczbą
     * @param kind    kolor liczby: neutral, good, bad albo accent
     */
    public StatTile(String caption, String kind) {
        super(2);
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        value.getStyleClass().addAll("stat-value", "stat-" + kind);
        getStyleClass().add("stat-tile");
        getChildren().addAll(value, captionLabel);
    }

    public void setValue(Object newValue) {
        value.setText(String.valueOf(newValue));
    }
}

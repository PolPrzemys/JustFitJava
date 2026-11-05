package pl.justfit.client;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wspólne drobiazgi dla zakładek: wywołania sieciowe w tle, okna komunikatów i kolumny tabel.
 * Wywołanie sieciowe nigdy nie działa w wątku GUI, żeby okno nie zawieszało się na czas odpowiedzi serwera.
 */
public final class Ui {

    private static final Logger log = LogManager.getLogger(Ui.class);

    /** Czynność bez wyniku, która może rzucić wyjątek (np. błąd sieci). */
    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    /** Wspólny arkusz stylów z modułu common. Gdyby go zabrakło, aplikacja działa w domyślnym wyglądzie. */
    private static final URL STYLESHEET = Ui.class.getResource("/justfit.css");

    private static Runnable onConnectionLost = () -> { };

    private Ui() {
    }

    /** Nakłada wygląd JustFit na scenę. */
    public static void style(Scene scene) {
        if (STYLESHEET != null) {
            scene.getStylesheets().add(STYLESHEET.toExternalForm());
        }
    }

    /** Okna dialogowe mają własną scenę, więc styl trzeba nałożyć też na nie (dotyczy też Alert). */
    public static void style(Dialog<?> dialog) {
        if (STYLESHEET != null) {
            dialog.getDialogPane().getStylesheets().add(STYLESHEET.toExternalForm());
        }
    }

    /** Co zrobić, gdy zniknie połączenie z serwerem (aplikacja wraca do ekranu logowania). */
    public static void setConnectionLostHandler(Runnable handler) {
        onConnectionLost = handler;
    }

    /** Wykonuje {@code work} w tle, a wynik oddaje do {@code onSuccess} już w wątku GUI. */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> handleFailure(task.getException()));
        Thread thread = new Thread(task, "network");
        thread.setDaemon(true);
        thread.start();
    }

    /** To samo co {@link #run}, ale dla czynności bez wyniku. */
    public static void runVoid(Action work, Runnable onSuccess) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run());
    }

    private static void handleFailure(Throwable error) {
        if (error instanceof ServiceException) {
            // błąd "biznesowy" z serwera, np. brak miejsc: pokazujemy jego komunikat
            warn(error.getMessage());
            return;
        }
        log.warn("Błąd połączenia: {}", error.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR, "Błąd połączenia z serwerem: " + error.getMessage());
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
        onConnectionLost.run();
    }

    public static void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }

    public static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        style(alert);
        return alert.showAndWait().filter(button -> button == ButtonType.YES).isPresent();
    }

    /** Kolumna tabeli wyświetlająca tekst obliczony z wiersza. Pusta wartość (null) pokazuje się jako pusty tekst. */
    public static <S> TableColumn<S, String> column(String title, double width, Function<S, String> text) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            String value = text.apply(cell.getValue());
            return new ReadOnlyStringWrapper(value == null ? "" : value);
        });
        column.setPrefWidth(width);
        return column;
    }

    /**
     * Kolumna z kolorową „odznaką” zamiast zwykłego tekstu (np. status karnetu).
     *
     * @param kind zamienia tekst na rodzaj odznaki: good, warn, bad albo muted (kolory w justfit.css)
     */
    public static <S> TableColumn<S, String> badgeColumn(String title, double width,
                                                          Function<S, String> text, Function<String, String> kind) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            String value = text.apply(cell.getValue());
            return new ReadOnlyStringWrapper(value == null ? "" : value);
        });
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isEmpty()) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(value);
                badge.getStyleClass().addAll("badge", "badge-" + kind.apply(value));
                setGraphic(badge);
            }
        });
        column.setPrefWidth(width);
        return column;
    }
}

package pl.justfit.client;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.ReadOnlyStringWrapper;

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

    private static Runnable onConnectionLost = () -> { };

    private Ui() {
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
        alert.showAndWait();
        onConnectionLost.run();
    }

    public static void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
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
}

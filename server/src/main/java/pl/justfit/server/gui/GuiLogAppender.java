package pl.justfit.server.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Appender Log4j2 przekazujący każdą linię logu słuchaczom (okno serwera pokazuje je na żywo).
 * Działa obok appendera plikowego z log4j2.xml, więc zapis do pliku zostaje bez zmian.
 */
public final class GuiLogAppender extends AbstractAppender {

    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static boolean installed;

    private GuiLogAppender() {
        super("GuiLog", null, null, true, Property.EMPTY_ARRAY);
    }

    /** Podpina appender do głównego loggera. Wielokrotne wywołanie nic nie zmienia. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        GuiLogAppender appender = new GuiLogAppender();
        appender.start();
        configuration.addAppender(appender);
        configuration.getRootLogger().addAppender((Appender) appender, null, null);
        context.updateLoggers();
        installed = true;
    }

    public static void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void append(LogEvent event) {
        StringBuilder line = new StringBuilder()
                .append(TIME.format(Instant.ofEpochMilli(event.getTimeMillis())))
                .append(' ')
                .append(String.format("%-5s", event.getLevel()))
                .append(" [").append(event.getThreadName()).append("] ")
                .append(event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
            line.append(" | ").append(event.getThrown());
        }
        String text = line.toString();
        for (Consumer<String> listener : LISTENERS) {
            listener.accept(text);
        }
    }
}

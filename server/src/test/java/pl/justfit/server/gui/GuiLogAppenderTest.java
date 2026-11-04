package pl.justfit.server.gui;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiLogAppenderTest {

    @Test
    void installedAppenderDeliversLogLinesToListeners() {
        GuiLogAppender.install();
        List<String> lines = new CopyOnWriteArrayList<>();
        Consumer<String> listener = lines::add;
        GuiLogAppender.addListener(listener);
        try {
            LogManager.getLogger("test.gui").info("witaj {}", "swiecie");
            assertTrue(lines.stream().anyMatch(l -> l.contains("witaj swiecie") && l.contains("INFO")),
                    "oczekiwano linii logu, otrzymano: " + lines);
        } finally {
            GuiLogAppender.removeListener(listener);
        }
    }

    @Test
    void removedListenerNoLongerReceivesLines() {
        GuiLogAppender.install();
        List<String> lines = new CopyOnWriteArrayList<>();
        Consumer<String> listener = lines::add;
        GuiLogAppender.addListener(listener);
        GuiLogAppender.removeListener(listener);

        LogManager.getLogger("test.gui").info("nikt tego nie slyszy");
        assertEquals(0, lines.size());
    }

    @Test
    void installIsIdempotent() {
        GuiLogAppender.install();
        GuiLogAppender.install();
        List<String> lines = new CopyOnWriteArrayList<>();
        Consumer<String> listener = lines::add;
        GuiLogAppender.addListener(listener);
        try {
            LogManager.getLogger("test.gui").info("jedna linia");
            long count = lines.stream().filter(l -> l.contains("jedna linia")).count();
            assertEquals(1, count, "linia nie powinna być zdublowana");
        } finally {
            GuiLogAppender.removeListener(listener);
        }
    }
}

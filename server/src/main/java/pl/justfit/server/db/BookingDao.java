package pl.justfit.server.db;

import pl.justfit.common.Booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Zapisy członków na zajęcia wraz z regułami (limit miejsc, ważność karnetu, duplikaty). */
public class BookingDao {

    /**
     * Wspólny zamek dla wszystkich wątków serwera. Sprawdzenie wolnych miejsc i zapis
     * muszą się odbyć razem, inaczej dwóch klientów mogłoby zająć ostatnie miejsce naraz.
     * Wystarcza zwykły synchronized, bo działa jeden proces serwera.
     */
    private static final Object LOCK = new Object();

    private final Database database;

    public BookingDao(Database database) {
        this.database = database;
    }

    public Booking book(long sessionId, long memberId) throws SQLException, BusinessRuleException {
        synchronized (LOCK) {
            try (Connection c = database.connection()) {
                int capacity;
                LocalDateTime start;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT capacity, start_time FROM class_sessions WHERE id = ?")) {
                    ps.setLong(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new BusinessRuleException("Nie znaleziono zajęć");
                        }
                        capacity = rs.getInt(1);
                        start = rs.getTimestamp(2).toLocalDateTime();
                    }
                }

                String memberName;
                String membershipNumber;
                LocalDate expiration;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT name, membership_number, expiration_date FROM members WHERE id = ?")) {
                    ps.setLong(1, memberId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new BusinessRuleException("Nie znaleziono członka");
                        }
                        memberName = rs.getString(1);
                        membershipNumber = rs.getString(2);
                        java.sql.Date date = rs.getDate(3);
                        expiration = date == null ? null : date.toLocalDate();
                    }
                }

                if (start.isBefore(LocalDateTime.now())) {
                    throw new BusinessRuleException("Te zajęcia już się odbyły");
                }
                if (expiration == null || expiration.isBefore(start.toLocalDate())) {
                    throw new BusinessRuleException("Karnet członka nie jest ważny w dniu zajęć");
                }
                if (count(c, "SELECT COUNT(*) FROM bookings WHERE session_id = ? AND member_id = ?",
                        sessionId, memberId) > 0) {
                    throw new BusinessRuleException("Członek jest już zapisany na te zajęcia");
                }
                if (count(c, "SELECT COUNT(*) FROM bookings WHERE session_id = ?", sessionId) >= capacity) {
                    throw new BusinessRuleException("Brak wolnych miejsc");
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bookings (session_id, member_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, memberId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        return new Booking(keys.getLong(1), sessionId, memberId, memberName, membershipNumber);
                    }
                }
            }
        }
    }

    /** @return false, gdy zapis o podanym id nie istnieje */
    public boolean cancel(long bookingId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
            ps.setLong(1, bookingId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Booking> listForSession(long sessionId) throws SQLException {
        List<Booking> result = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT b.id, b.session_id, b.member_id, m.name, m.membership_number
                     FROM bookings b
                     JOIN members m ON m.id = b.member_id
                     WHERE b.session_id = ?
                     ORDER BY m.name, b.id""")) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Booking(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                            rs.getString(4), rs.getString(5)));
                }
            }
        }
        return result;
    }

    private int count(Connection c, String sql, long... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setLong(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}

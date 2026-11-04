package pl.justfit.server.db;

import pl.justfit.common.ClassSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Operacje na tabeli zajęć. Liczbę zapisanych osób liczy baza przy odczycie. */
public class SessionDao {

    private final Database database;

    public SessionDao(Database database) {
        this.database = database;
    }

    public ClassSession add(ClassSession session) throws SQLException, BusinessRuleException {
        try (Connection c = database.connection()) {
            String trainerName = findTrainerName(c, session.getTrainerId());
            if (trainerName == null) {
                throw new BusinessRuleException("Nie znaleziono trenera");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO class_sessions (title, trainer_id, room, start_time, capacity) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, session.getTitle());
                ps.setLong(2, session.getTrainerId());
                ps.setString(3, session.getRoom());
                ps.setTimestamp(4, Timestamp.valueOf(session.startAsDateTime()));
                ps.setInt(5, session.getCapacity());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    session.setId(keys.getLong(1));
                }
            }
            session.setTrainerName(trainerName);
            session.setBooked(0);
            return session;
        }
    }

    public List<ClassSession> list() throws SQLException {
        List<ClassSession> result = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT s.id, s.title, s.trainer_id, t.name, s.room, s.start_time, s.capacity,
                            (SELECT COUNT(*) FROM bookings b WHERE b.session_id = s.id)
                     FROM class_sessions s
                     JOIN trainers t ON t.id = s.trainer_id
                     ORDER BY s.start_time, s.id""");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ClassSession(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6).toLocalDateTime().toString(),
                        rs.getInt(7), rs.getInt(8)));
            }
        }
        return result;
    }

    /**
     * Usuwa zajęcia razem z zapisami (kaskada w bazie).
     *
     * @return false, gdy zajęcia o podanym id nie istnieją
     */
    public boolean delete(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM class_sessions WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private String findTrainerName(Connection c, long trainerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM trainers WHERE id = ?")) {
            ps.setLong(1, trainerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}

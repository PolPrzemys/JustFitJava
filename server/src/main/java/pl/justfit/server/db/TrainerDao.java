package pl.justfit.server.db;

import pl.justfit.common.Trainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Operacje na tabeli trenerów. */
public class TrainerDao {

    private final Database database;

    public TrainerDao(Database database) {
        this.database = database;
    }

    public Trainer add(Trainer trainer) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO trainers (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, trainer.getName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                trainer.setId(keys.getLong(1));
            }
            return trainer;
        }
    }

    public List<Trainer> list() throws SQLException {
        List<Trainer> result = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT id, name FROM trainers ORDER BY name, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Trainer(rs.getLong(1), rs.getString(2)));
            }
        }
        return result;
    }

    /**
     * Usuwa trenera. Trener z zaplanowanymi zajęciami nie może zostać usunięty.
     *
     * @return false, gdy trener o podanym id nie istnieje
     */
    public boolean delete(long id) throws SQLException, BusinessRuleException {
        try (Connection c = database.connection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM class_sessions WHERE trainer_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new BusinessRuleException("Trener ma zaplanowane zajęcia. Najpierw usuń te zajęcia.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM trainers WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }
}

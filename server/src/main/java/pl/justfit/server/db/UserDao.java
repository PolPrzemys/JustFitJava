package pl.justfit.server.db;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Konta pracowników klubu. Hasła są przechowywane wyłącznie jako hash BCrypt. */
public class UserDao {

    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public void create(String username, String password) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO app_users (username, password_hash) VALUES (?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }

    public boolean authenticate(String username, String password) throws SQLException {
        if (username == null || password == null) {
            return false;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM app_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && BCrypt.checkpw(password, rs.getString(1));
            }
        }
    }

    public int count() throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM app_users");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

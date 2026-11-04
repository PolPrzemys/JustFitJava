package pl.justfit.server.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Dostęp do bazy H2. Każda operacja pobiera własne połączenie,
 * dzięki czemu wiele wątków klientów może pracować równolegle bez współdzielenia połączenia.
 */
public class Database {

    private static final Logger log = LogManager.getLogger(Database.class);

    static final String DEFAULT_ADMIN = "admin";
    static final String DEFAULT_PASSWORD = "admin123";

    private final String url;

    public Database(String url) throws SQLException {
        this.url = url;
        init();
    }

    /** Baza plikowa, trwała między uruchomieniami (np. ./data/justfit). */
    public static Database file(String path) throws SQLException {
        return new Database("jdbc:h2:" + path);
    }

    /** Baza w pamięci, używana w testach. Każda nazwa to osobna baza. */
    public static Database inMemory(String name) throws SQLException {
        return new Database("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private void init() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS app_users (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL UNIQUE,
                        password_hash VARCHAR(100) NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS members (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        membership_number VARCHAR(50) NOT NULL UNIQUE,
                        expiration_date DATE
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS trainers (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS class_sessions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        trainer_id BIGINT NOT NULL REFERENCES trainers(id),
                        room VARCHAR(100) NOT NULL,
                        start_time TIMESTAMP NOT NULL,
                        capacity INT NOT NULL CHECK (capacity > 0)
                    )""");
            // usunięcie zajęć albo członka usuwa też jego zapisy
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bookings (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        session_id BIGINT NOT NULL REFERENCES class_sessions(id) ON DELETE CASCADE,
                        member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                        UNIQUE (session_id, member_id)
                    )""");
        }
        UserDao users = new UserDao(this);
        if (users.count() == 0) {
            users.create(DEFAULT_ADMIN, DEFAULT_PASSWORD);
            log.warn("Utworzono domyślne konto '{}'. Zmień hasło przed użyciem produkcyjnym.", DEFAULT_ADMIN);
        }
        log.info("Baza danych gotowa: {}", url);
    }
}

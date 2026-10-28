package com.campusverse.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages JDBC connections and schema initialization for H2 database in persistent file mode.
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private static final String DEFAULT_DB_URL = "jdbc:h2:./data/campusverse_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to load H2 JDBC driver", e);
            throw new RuntimeException("H2 JDBC Driver not found", e);
        }
    }

    public static String getDatabaseUrl() {
        return System.getProperty("campusverse.db.url", DEFAULT_DB_URL);
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getDatabaseUrl(), DB_USER, DB_PASSWORD);
    }

    /**
     * Idempotently initializes database schema and seeds initial data.
     */
    public static synchronized void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // 1. Students table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    nickname VARCHAR(50) NOT NULL UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // 2. Interests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS interests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL UNIQUE
                );
            """);

            // 3. Student interests mapping
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS student_interests (
                    student_id BIGINT NOT NULL,
                    interest_id BIGINT NOT NULL,
                    PRIMARY KEY (student_id, interest_id),
                    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
                    FOREIGN KEY (interest_id) REFERENCES interests(id) ON DELETE CASCADE
                );
            """);

            // 4. Rooms table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NULL,
                    status VARCHAR(20) NOT NULL
                );
            """);

            // 5. Messages table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    room_id BIGINT NOT NULL,
                    student_id BIGINT NOT NULL,
                    content VARCHAR(2000) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
                    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
                );
            """);

            // 6. Doubts table (for Phase 3)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS doubts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    student_id BIGINT NOT NULL,
                    subject VARCHAR(50) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    description CLOB NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
                );
            """);

            // 7. Doubt answers table (for Phase 3)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS doubt_answers (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    doubt_id BIGINT NOT NULL,
                    student_id BIGINT NOT NULL,
                    answer_text CLOB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (doubt_id) REFERENCES doubts(id) ON DELETE CASCADE,
                    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
                );
            """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_room ON messages(room_id, id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rooms_type ON rooms(type, status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_doubts_subject ON doubts(subject, status);");

            // Seed default STUDY rooms (Java, DSA, DBMS, General)
            seedDefaultStudyRooms(conn);

            // Seed predefined interests
            seedDefaultInterests(conn);

            LOGGER.info("Database schema initialized and default data verified.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database schema initialization failed", e);
            throw new RuntimeException("Database initialization error", e);
        }
    }

    private static void seedDefaultStudyRooms(Connection conn) throws SQLException {
        String[] defaultRooms = {"Java", "DSA", "DBMS", "General"};
        String checkSql = "SELECT COUNT(*) FROM rooms WHERE name = ? AND type = 'STUDY'";
        String insertSql = "INSERT INTO rooms (name, type, status) VALUES (?, 'STUDY', 'ACTIVE')";

        for (String roomName : defaultRooms) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, roomName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                            insStmt.setString(1, roomName);
                            insStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private static void seedDefaultInterests(Connection conn) throws SQLException {
        String[] defaultInterests = {
            "Java", "Python", "DSA", "Web Development", "UI/UX", "Hackathons", "Gaming", "Photography", "AI", "DBMS", "React"
        };
        String checkSql = "SELECT COUNT(*) FROM interests WHERE name = ?";
        String insertSql = "INSERT INTO interests (name) VALUES (?)";

        for (String interest : defaultInterests) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, interest);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                            insStmt.setString(1, interest);
                            insStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }
}


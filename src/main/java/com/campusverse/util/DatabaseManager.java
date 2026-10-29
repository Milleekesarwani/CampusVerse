package com.campusverse.util;

import java.io.File;
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

    private static volatile String resolvedDbUrl = null;

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to load H2 JDBC driver", e);
            throw new RuntimeException("H2 JDBC Driver not found", e);
        }
    }

    public static String getDatabaseUrl() {
        String prop = System.getProperty("campusverse.db.url");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }

        if (resolvedDbUrl != null) {
            return resolvedDbUrl;
        }

        synchronized (DatabaseManager.class) {
            if (resolvedDbUrl != null) {
                return resolvedDbUrl;
            }

            String dataDirProp = System.getProperty("campusverse.data.dir");
            File dataDir = null;
            if (dataDirProp != null && !dataDirProp.trim().isEmpty()) {
                dataDir = new File(dataDirProp.trim());
            } else {
                try {
                    File localDir = new File(".").getCanonicalFile();
                    String localPath = localDir.getAbsolutePath().toLowerCase();
                    if (!localPath.contains("system32") && !localPath.contains("windows")) {
                        File candidate = new File(localDir, "data");
                        if (candidate.exists() || candidate.mkdirs() || candidate.canWrite()) {
                            dataDir = candidate;
                        }
                    }
                } catch (Exception ignored) {}

                if (dataDir == null) {
                    String catalinaBase = System.getProperty("catalina.base");
                    if (catalinaBase != null && !catalinaBase.trim().isEmpty()) {
                        File catData = new File(catalinaBase, "data");
                        catData.mkdirs();
                        dataDir = catData;
                    }
                }

                if (dataDir == null) {
                    File homeData = new File(System.getProperty("user.home"), ".campusverse/data");
                    homeData.mkdirs();
                    dataDir = homeData;
                }
            }

            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            String normalizedPath = new File(dataDir, "campusverse_db").getAbsolutePath().replace('\\', '/');
            resolvedDbUrl = "jdbc:h2:" + normalizedPath + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
            LOGGER.info("Resolved CampusVerse H2 database URL: " + resolvedDbUrl);
            return resolvedDbUrl;
        }
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

            // Seed initial campus peers, doubts, and ghost rooms
            seedDefaultPeersAndDoubts(conn);

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

    private static void seedDefaultPeersAndDoubts(Connection conn) throws SQLException {
        // 1. Seed students
        String[][] peers = {
            {"Milee_Kesarwani", "Java", "DSA", "Hackathons"},
            {"Aman_Sharma", "Java", "React", "Hackathons"},
            {"Riya_Verma", "Java", "DSA", "Hackathons"},
            {"Kabir_Singh", "UI/UX", "React", "Gaming"},
            {"Ananya_Das", "DBMS", "Python", "DSA"}
        };

        String checkStu = "SELECT id FROM students WHERE nickname = ?";
        String insStu = "INSERT INTO students (nickname) VALUES (?)";
        String insInter = "MERGE INTO student_interests (student_id, interest_id) KEY(student_id, interest_id) " +
                          "SELECT ?, id FROM interests WHERE name = ?";

        for (String[] peer : peers) {
            String nick = peer[0];
            Long stuId = null;
            try (PreparedStatement cs = conn.prepareStatement(checkStu)) {
                cs.setString(1, nick);
                try (ResultSet rs = cs.executeQuery()) {
                    if (rs.next()) {
                        stuId = rs.getLong(1);
                    }
                }
            }
            if (stuId == null) {
                try (PreparedStatement is = conn.prepareStatement(insStu, Statement.RETURN_GENERATED_KEYS)) {
                    is.setString(1, nick);
                    is.executeUpdate();
                    try (ResultSet rs = is.getGeneratedKeys()) {
                        if (rs.next()) stuId = rs.getLong(1);
                    }
                }
            }
            if (stuId != null) {
                for (int i = 1; i < peer.length; i++) {
                    try (PreparedStatement mi = conn.prepareStatement(insInter)) {
                        mi.setLong(1, stuId);
                        mi.setString(2, peer[i]);
                        mi.executeUpdate();
                    }
                }
            }
        }

        // 2. Seed initial doubts if table is empty
        String checkDoubts = "SELECT COUNT(*) FROM doubts";
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(checkDoubts)) {
            if (rs.next() && rs.getInt(1) == 0) {
                Long riyaId = getStudentIdByNick(conn, "Riya_Verma");
                Long amanId = getStudentIdByNick(conn, "Aman_Sharma");
                Long ananyaId = getStudentIdByNick(conn, "Ananya_Das");
                Long kabirId = getStudentIdByNick(conn, "Kabir_Singh");

                if (riyaId != null && amanId != null) {
                    Long d1 = insertDoubt(conn, riyaId, "Java",
                            "Can someone explain method overloading with a real-life example?",
                            "I understand the syntax, but when should I use it instead of creating a new method name?", "OPEN");
                    if (d1 != null) {
                        insertAnswer(conn, d1, amanId,
                                "Use overloading when the operation is the same but the input differs — like add(int, int) and add(double, double). Java picks the method from the argument types.");
                    }
                }

                if (ananyaId != null) {
                    insertDoubt(conn, ananyaId, "DBMS",
                            "A simple way to remember 2NF vs 3NF?",
                            "Partial and transitive dependencies keep getting mixed up in my head. Any good examples?", "OPEN");
                }

                if (kabirId != null && riyaId != null) {
                    Long d3 = insertDoubt(conn, kabirId, "DSA",
                            "Why is binary search O(log n)?",
                            "What happens to the search space in each step? Looking for an intuitive explanation.", "RESOLVED");
                    if (d3 != null) {
                        insertAnswer(conn, d3, riyaId,
                                "Each comparison halves the search space. After k steps, n / 2^k = 1, so k = log₂(n).");
                    }
                }
            }
        }

        // 3. Seed initial ghost rooms if none exist
        String checkGhosts = "SELECT COUNT(*) FROM rooms WHERE type = 'GHOST'";
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(checkGhosts)) {
            if (rs.next() && rs.getInt(1) == 0) {
                long now = System.currentTimeMillis();
                insertGhostRoom(conn, "DBMS: the last-minute club", new java.sql.Timestamp(now + 2 * 3600 * 1000));
                insertGhostRoom(conn, "Two pointers, one brain cell", new java.sql.Timestamp(now + 4 * 3600 * 1000));
                insertGhostRoom(conn, "Hackathon idea jam", new java.sql.Timestamp(now + 8 * 3600 * 1000));
            }
        }
    }

    private static Long getStudentIdByNick(Connection conn, String nick) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM students WHERE nickname = ?")) {
            ps.setString(1, nick);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Long insertDoubt(Connection conn, Long stuId, String subject, String title, String desc, String status) throws SQLException {
        String sql = "INSERT INTO doubts (student_id, subject, title, description, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, stuId);
            ps.setString(2, subject);
            ps.setString(3, title);
            ps.setString(4, desc);
            ps.setString(5, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static void insertAnswer(Connection conn, Long doubtId, Long stuId, String text) throws SQLException {
        String sql = "INSERT INTO doubt_answers (doubt_id, student_id, answer_text) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, doubtId);
            ps.setLong(2, stuId);
            ps.setString(3, text);
            ps.executeUpdate();
        }
    }

    private static void insertGhostRoom(Connection conn, String name, java.sql.Timestamp expiresAt) throws SQLException {
        String sql = "INSERT INTO rooms (name, type, status, expires_at) VALUES (?, 'GHOST', 'ACTIVE', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setTimestamp(2, expiresAt);
            ps.executeUpdate();
        }
    }
}


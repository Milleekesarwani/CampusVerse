package com.campusverse;

import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.servlet.StudentServlet;
import com.campusverse.util.DatabaseManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase1DatabaseAndStudentTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_phase1_test";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    private static StudentDAO studentDAO;
    private static RoomDAO roomDAO;

    @BeforeAll
    static void setUp() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        roomDAO = new RoomDAO();
    }

    @Test
    @Order(1)
    void testVerifyExactlyFourDefaultSubjectRooms() throws SQLException {
        List<Room> rooms = roomDAO.findStudyRooms();
        assertNotNull(rooms);
        assertEquals(4, rooms.size(), "Must have exactly 4 default subject rooms");

        List<String> names = rooms.stream().map(Room::getName).sorted().collect(Collectors.toList());
        assertEquals(List.of("DBMS", "DSA", "General", "Java"), names);

        for (Room r : rooms) {
            assertEquals("STUDY", r.getType());
            assertEquals("ACTIVE", r.getStatus());
            assertNull(r.getExpiresAt(), "Study rooms must not have an expiration timestamp");
        }
    }

    @Test
    @Order(2)
    void testVerifyDefaultInterestsSeeded() throws SQLException {
        List<Interest> interests = studentDAO.getAllInterests();
        assertNotNull(interests);
        assertTrue(interests.size() >= 9, "Should seed at least 9 predefined interests");

        List<String> names = interests.stream().map(Interest::getName).collect(Collectors.toList());
        assertTrue(names.contains("Java"));
        assertTrue(names.contains("Python"));
        assertTrue(names.contains("DSA"));
        assertTrue(names.contains("Web Development"));
        assertTrue(names.contains("UI/UX"));
        assertTrue(names.contains("Hackathons"));
        assertTrue(names.contains("Gaming"));
        assertTrue(names.contains("Photography"));
        assertTrue(names.contains("AI"));
    }

    @Test
    @Order(3)
    void testCreateStudentAndConfirmSavedInH2() throws SQLException {
        String testNick = "Phase1Tester";
        Student s = studentDAO.findOrCreateByNickname(testNick);
        assertNotNull(s);
        assertNotNull(s.getId());
        assertEquals("Phase1Tester", s.getNickname());
        assertNotNull(s.getCreatedAt());

        // Confirm directly via raw SQL query in H2
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, nickname FROM students WHERE nickname = 'Phase1Tester'")) {
            assertTrue(rs.next(), "Student record must exist in H2 students table");
            assertEquals(s.getId(), rs.getLong("id"));
            assertEquals("Phase1Tester", rs.getString("nickname"));
        }
    }

    @Test
    @Order(4)
    void testSaveMultipleInterestsRefreshAndVerify() throws SQLException {
        Student s = studentDAO.findOrCreateByNickname("MultiInterestStudent");
        List<Interest> allInterests = studentDAO.getAllInterests();
        assertTrue(allInterests.size() >= 4);

        // Pick 3 interests: Java, DSA, AI
        List<Long> selectedIds = List.of(
                allInterests.get(0).getId(),
                allInterests.get(1).getId(),
                allInterests.get(2).getId()
        );
        studentDAO.setStudentInterests(s.getId(), selectedIds);

        // Refresh/re-fetch from DB
        List<Interest> savedInterests = studentDAO.getStudentInterests(s.getId());
        assertEquals(3, savedInterests.size(), "Must persist all 3 selected interests");

        List<Long> savedIds = savedInterests.stream().map(Interest::getId).collect(Collectors.toList());
        assertTrue(savedIds.containsAll(selectedIds));

        // Update to 1 interest and verify it replaces previous selection without orphan records
        studentDAO.setStudentInterests(s.getId(), List.of(allInterests.get(0).getId()));
        List<Interest> updatedInterests = studentDAO.getStudentInterests(s.getId());
        assertEquals(1, updatedInterests.size());
        assertEquals(allInterests.get(0).getId(), updatedInterests.get(0).getId());
    }

    @Test
    @Order(5)
    void testRejectBlankAndInvalidNicknames() {
        // Null or blank
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname(null));
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname(""));
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname("   "));

        // Whitespaces or special symbols not allowed in student nickname
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname("Bad Nick With Spaces"));
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname("Student<script>"));
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname("Drop*Table!"));

        // Too long (> 50 chars)
        assertThrows(IllegalArgumentException.class, () -> studentDAO.findOrCreateByNickname("a".repeat(51)));
    }

    @Test
    @Order(6)
    void testFileBackedH2PersistenceSurvivesDatabaseRestart() throws SQLException {
        String persistentNick = "PersistentStudent";
        Student s = studentDAO.findOrCreateByNickname(persistentNick);
        List<Interest> allInterests = studentDAO.getAllInterests();
        studentDAO.setStudentInterests(s.getId(), List.of(allInterests.get(0).getId()));

        // Simulate database checkpoint
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CHECKPOINT");
            }
        }

        // Open a brand new connection to the file database and verify data persisted
        try (Connection newConn = DriverManager.getConnection(TEST_DB_URL, "sa", "")) {
            try (Statement stmt = newConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT nickname FROM students WHERE id = " + s.getId())) {
                assertTrue(rs.next(), "Student must persist on disk across database reloads");
                assertEquals(persistentNick, rs.getString("nickname"));
            }

            try (Statement stmt = newConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_interests WHERE student_id = " + s.getId())) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Interests association must persist on disk across database reloads");
            }
        }
    }

    @Test
    @Order(7)
    void testSchemaInitializationIsIdempotent() {
        // Re-executing initSchema multiple times must not crash or duplicate default rooms
        assertDoesNotThrow(DatabaseManager::initSchema);
        assertDoesNotThrow(DatabaseManager::initSchema);

        assertDoesNotThrow(() -> {
            List<Room> rooms = roomDAO.findStudyRooms();
            assertEquals(4, rooms.size(), "Still exactly 4 study rooms after multiple initSchema calls");
        });
    }

    @Test
    @Order(8)
    void testRejectProtectedActionsWithoutActiveNicknameSession() throws Exception {
        StudentServlet servlet = new StudentServlet();

        // Native Java 21 reflection proxy for request
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getParameter".equals(name) && args != null && args.length > 0) {
                        if ("action".equals(args[0])) return "updateInterests";
                    }
                    if ("getSession".equals(name)) return null; // No session
                    if ("getContextPath".equals(name)) return "/campusverse-lite";
                    return null;
                }
        );

        final String[] redirectLocation = new String[1];
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendRedirect".equals(method.getName()) && args != null && args.length > 0) {
                        redirectLocation[0] = (String) args[0];
                    }
                    return null;
                }
        );

        servlet.service(req, resp);

        // Verify that unprotected request without session is rejected and redirected to home
        assertNotNull(redirectLocation[0]);
        assertTrue(redirectLocation[0].contains("/home"), "Must redirect unauthenticated request to /home");
    }
}

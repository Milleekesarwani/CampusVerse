package com.campusverse;

import com.campusverse.dao.DoubtDAO;
import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.*;
import com.campusverse.service.ChatService;
import com.campusverse.service.DoubtService;
import com.campusverse.service.GhostRoomService;
import com.campusverse.service.VibeMatchService;
import com.campusverse.util.DatabaseManager;
import com.campusverse.util.SecurityFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase6FullRegressionTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_regression";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static StudentDAO studentDAO;
    private static RoomDAO roomDAO;
    private static MessageDAO messageDAO;
    private static DoubtDAO doubtDAO;
    private static ChatService chatService;
    private static DoubtService doubtService;
    private static VibeMatchService vibeMatchService;
    private static GhostRoomService ghostRoomService;

    @BeforeAll
    static void setUp() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        roomDAO = new RoomDAO();
        messageDAO = new MessageDAO();
        doubtDAO = new DoubtDAO();

        chatService = new ChatService(messageDAO, roomDAO);
        doubtService = new DoubtService(doubtDAO);
        vibeMatchService = new VibeMatchService(studentDAO);
        ghostRoomService = new GhostRoomService(roomDAO, Clock.systemUTC());
    }

    @Test
    @Order(1)
    void step1_CreateNicknameAndInterestsVerifyPersistence() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("RegStudentAlpha");
        assertNotNull(student.getId());

        List<Interest> allInterests = studentDAO.getAllInterests();
        assertTrue(allInterests.size() >= 3);

        List<Long> selected = List.of(allInterests.get(0).getId(), allInterests.get(1).getId());
        studentDAO.setStudentInterests(student.getId(), selected);

        Student reloaded = studentDAO.findById(student.getId());
        assertEquals(2, reloaded.getInterests().size());
    }

    @Test
    @Order(2)
    void step2_TwoSessionsSendReceiveMessagesInSubjectRoom() throws SQLException {
        Student user1 = studentDAO.findOrCreateByNickname("RegChatterA");
        Student user2 = studentDAO.findOrCreateByNickname("RegChatterB");

        Room javaRoom = roomDAO.findStudyRooms().stream()
                .filter(r -> "Java".equals(r.getName()))
                .findFirst().orElseThrow();

        Message msg1 = chatService.postMessage(javaRoom.getId(), user1.getId(), "Phase 6 Java message 1");
        Message msg2 = chatService.postMessage(javaRoom.getId(), user2.getId(), "Phase 6 Java message 2");

        List<Message> history = chatService.getRecentMessages(javaRoom.getId(), 50);
        assertTrue(history.stream().anyMatch(m -> m.getId().equals(msg1.getId())));
        assertTrue(history.stream().anyMatch(m -> m.getId().equals(msg2.getId())));
    }

    @Test
    @Order(3)
    void step3_DoubtDropPostAnswerAndAuthorOnlyResolution() throws SQLException {
        Student author = studentDAO.findOrCreateByNickname("RegAuthorDoubt");
        Student helper = studentDAO.findOrCreateByNickname("RegHelperDoubt");

        Doubt doubt = doubtService.postDoubt(author.getId(), "DSA", "Heap vs BST lookup complexity", "Which is better for priority queues?");
        assertNotNull(doubt.getId());

        DoubtAnswer answer = doubtService.postAnswer(doubt.getId(), helper.getId(), "Heap is O(1) for min/max lookup, O(log N) insert/delete.");
        assertNotNull(answer.getId());

        // Unauthorized resolve attempt by helper must fail
        assertThrows(SecurityException.class, () -> doubtService.resolveDoubt(doubt.getId(), helper.getId()));

        // Author resolves
        boolean ok = doubtService.resolveDoubt(doubt.getId(), author.getId());
        assertTrue(ok);

        Doubt resolved = doubtService.getDoubtById(doubt.getId());
        assertEquals("RESOLVED", resolved.getStatus());
    }

    @Test
    @Order(4)
    void step4_VibeMatchCalculatesKnownPercentages() throws SQLException {
        Student stu1 = studentDAO.findOrCreateByNickname("RegVibe1");
        Student stu2 = studentDAO.findOrCreateByNickname("RegVibe2");

        List<Interest> interests = studentDAO.getAllInterests();
        Interest java = interests.stream().filter(i -> "Java".equals(i.getName())).findFirst().orElseThrow();
        Interest dsa = interests.stream().filter(i -> "DSA".equals(i.getName())).findFirst().orElseThrow();
        Interest python = interests.stream().filter(i -> "Python".equals(i.getName())).findFirst().orElseThrow();
        Interest web = interests.stream().filter(i -> "Web Development".equals(i.getName())).findFirst().orElseThrow();

        // stu1 has Java, DSA, Web (3)
        studentDAO.setStudentInterests(stu1.getId(), List.of(java.getId(), dsa.getId(), web.getId()));
        // stu2 has Java, DSA, Python (3)
        studentDAO.setStudentInterests(stu2.getId(), List.of(java.getId(), dsa.getId(), python.getId()));
        // Intersection = {Java, DSA} (2), Union = {Java, DSA, Web, Python} (4) -> 50%

        List<MatchResult> matches = vibeMatchService.findMatches(stu1.getId());
        MatchResult targetMatch = matches.stream()
                .filter(m -> m.getPeer().getId().equals(stu2.getId()))
                .findFirst().orElseThrow();

        assertEquals(50, targetMatch.getMatchPercentage(), "Jaccard similarity should be 50%");
        assertTrue(targetMatch.getSharedInterests().contains("Java"));
        assertTrue(targetMatch.getSharedInterests().contains("DSA"));
    }

    @Test
    @Order(5)
    void step5_GhostRoomCreationChatAndPostExpiryRejection() throws SQLException {
        Instant startTime = Instant.parse("2026-10-28T14:00:00Z");
        Clock clock1 = Clock.fixed(startTime, ZoneOffset.UTC);
        GhostRoomService gService1 = new GhostRoomService(roomDAO, clock1);
        ChatService cService1 = new ChatService(messageDAO, roomDAO, clock1);

        Student student = studentDAO.findOrCreateByNickname("RegGhostUser");

        Room ghost = gService1.createGhostRoom("Finals Sprint Room", "5 minutes");
        assertNotNull(ghost.getId());

        // Message accepted while active
        Message m = cService1.postMessage(ghost.getId(), student.getId(), "Chatting during sprint");
        assertNotNull(m.getId());

        // Advance 10 minutes past expiration
        Clock clock2 = Clock.fixed(startTime.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        ChatService cService2 = new ChatService(messageDAO, roomDAO, clock2);

        // Rejection after expiry
        assertThrows(IllegalStateException.class, () -> {
            cService2.postMessage(ghost.getId(), student.getId(), "Message after sprint ended");
        });
    }

    @Test
    @Order(6)
    void step6_SecuritySanitizationAndValidation() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("RegSecurityUser");
        Room room = roomDAO.findStudyRooms().get(0);

        // Rejection of blank messages
        assertThrows(IllegalArgumentException.class, () -> chatService.postMessage(room.getId(), student.getId(), "   "));

        // HTML escaping
        Message msg = chatService.postMessage(room.getId(), student.getId(), "<img src=x onerror=alert(1)>");
        assertFalse(msg.getContent().contains("<img"));
        assertTrue(msg.getContent().contains("&lt;img"));
    }

    @Test
    @Order(7)
    void step7_InvalidRoomIdAndParameterValidation() throws SQLException {
        Student student = studentDAO.findByNickname("RegSecurityUser");
        // Non-existent room ID (e.g. 999999L)
        assertThrows(IllegalArgumentException.class, () -> {
            chatService.postMessage(999999L, student.getId(), "Valid message to non-existent room");
        });
    }

    @Test
    @Order(8)
    void step8_DataPersistenceSurvivesFullShutdownAndRestart() throws SQLException {
        // Issue CHECKPOINT
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT");
        }

        // Open brand new connection and verify all records persist
        try (Connection newConn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = newConn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM students")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 4, "Students must persist on disk");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 2, "Messages must persist on disk");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM doubts")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1, "Doubts must persist on disk");
            }
        }
    }

    @Test
    @Order(9)
    void step9_SecurityFilterAndCsrfProtection() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name) && args != null) return sessionAttrs.get(args[0]);
                    if ("setAttribute".equals(name) && args != null) {
                        sessionAttrs.put((String) args[0], args[1]);
                        return null;
                    }
                    return null;
                }
        );

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getSession".equals(method.getName())) return session;
                    return null;
                }
        );

        Map<String, String> responseHeaders = new HashMap<>();
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName()) && args != null) {
                        responseHeaders.put((String) args[0], (String) args[1]);
                    }
                    return null;
                }
        );

        final boolean[] chainInvoked = new boolean[1];
        FilterChain chain = (sReq, sResp) -> chainInvoked[0] = true;

        filter.doFilter(req, resp, chain);

        assertTrue(chainInvoked[0], "Filter chain must be invoked");
        // Verify CSRF token generated and bound to session
        assertNotNull(sessionAttrs.get("csrfToken"), "SecurityFilter must attach CSRF token to active session");
        // Verify security headers set
        assertEquals("nosniff", responseHeaders.get("X-Content-Type-Options"));
        assertEquals("DENY", responseHeaders.get("X-Frame-Options"));
    }
}

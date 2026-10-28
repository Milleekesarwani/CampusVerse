package com.campusverse;

import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Message;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.ChatService;
import com.campusverse.servlet.ChatServlet;
import com.campusverse.servlet.MessageApiServlet;
import com.campusverse.util.DatabaseManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase2StudyChatTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_phase2_test";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static ChatService chatService;
    private static StudentDAO studentDAO;
    private static RoomDAO roomDAO;
    private static MessageDAO messageDAO;

    @BeforeAll
    static void setUp() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        roomDAO = new RoomDAO();
        messageDAO = new MessageDAO();
        chatService = new ChatService(messageDAO, roomDAO);
    }

    @Test
    @Order(1)
    void testTwoWayMessagingBetweenDistinctStudentSessions() throws SQLException {
        Student alice = studentDAO.findOrCreateByNickname("AlicePhase2");
        Student bob = studentDAO.findOrCreateByNickname("BobPhase2");

        List<Room> studyRooms = roomDAO.findStudyRooms();
        Room javaRoom = studyRooms.stream().filter(r -> "Java".equals(r.getName())).findFirst().orElseThrow();

        // 1. Alice sends message
        Message msgA = chatService.postMessage(javaRoom.getId(), alice.getId(), "Hello from Session A!");
        assertNotNull(msgA.getId());
        assertEquals("Hello from Session A!", msgA.getContent());

        // 2. Bob polls with afterId = 0 (sees Alice's message)
        List<Message> bPolled = chatService.getMessagesAfter(javaRoom.getId(), 0L);
        assertTrue(bPolled.stream().anyMatch(m -> m.getId().equals(msgA.getId())));

        // 3. Bob sends reply
        Message msgB = chatService.postMessage(javaRoom.getId(), bob.getId(), "Hello Alice, Session B received your message!");
        assertNotNull(msgB.getId());

        // 4. Alice polls with cursor afterId = msgA.getId() (sees ONLY Bob's reply)
        List<Message> aPolled = chatService.getMessagesAfter(javaRoom.getId(), msgA.getId());
        assertEquals(1, aPolled.size());
        assertEquals(msgB.getId(), aPolled.get(0).getId());
        assertEquals("BobPhase2", aPolled.get(0).getSenderNickname());
        assertEquals("Hello Alice, Session B received your message!", aPolled.get(0).getContent());

        // 5. Subsequent poll with afterId = msgB.getId() returns empty list (no duplicated messages)
        List<Message> emptyPoll = chatService.getMessagesAfter(javaRoom.getId(), msgB.getId());
        assertTrue(emptyPoll.isEmpty(), "Cursor-based polling should prevent duplicate messages");
    }

    @Test
    @Order(2)
    void testMessageHistoryPersistsAcrossDatabaseCheckpoint() throws SQLException {
        Student user = studentDAO.findOrCreateByNickname("PersistenceUser2");
        Room dsaRoom = roomDAO.findStudyRooms().stream().filter(r -> "DSA".equals(r.getName())).findFirst().orElseThrow();

        Message msg = chatService.postMessage(dsaRoom.getId(), user.getId(), "Critical algorithm note to persist");
        assertNotNull(msg.getId());

        // Force H2 disk checkpoint
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT");
        }

        // Connect afresh and verify message exists
        try (Connection newConn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = newConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT content FROM messages WHERE id = " + msg.getId())) {
            assertTrue(rs.next(), "Message must survive on disk across connection restarts");
            assertEquals("Critical algorithm note to persist", rs.getString("content"));
        }
    }

    @Test
    @Order(3)
    void testMessageRoomSeparation() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("RoomSeparationTester");
        List<Room> rooms = roomDAO.findStudyRooms();
        Room dsaRoom = rooms.stream().filter(r -> "DSA".equals(r.getName())).findFirst().orElseThrow();
        Room dbmsRoom = rooms.stream().filter(r -> "DBMS".equals(r.getName())).findFirst().orElseThrow();

        chatService.postMessage(dsaRoom.getId(), student.getId(), "DSA specific message: Graphs & BFS");
        chatService.postMessage(dbmsRoom.getId(), student.getId(), "DBMS specific message: ACID & Normalization");

        List<Message> dsaMessages = chatService.getRecentMessages(dsaRoom.getId(), 50);
        List<Message> dbmsMessages = chatService.getRecentMessages(dbmsRoom.getId(), 50);

        assertTrue(dsaMessages.stream().anyMatch(m -> m.getContent().contains("Graphs &amp; BFS")));
        assertFalse(dsaMessages.stream().anyMatch(m -> m.getContent().contains("ACID")));

        assertTrue(dbmsMessages.stream().anyMatch(m -> m.getContent().contains("ACID &amp; Normalization")));
        assertFalse(dbmsMessages.stream().anyMatch(m -> m.getContent().contains("Graphs")));
    }

    @Test
    @Order(4)
    void testMessageValidationAndXSSPrevention() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("XssDaveSec");
        Room room = roomDAO.findStudyRooms().get(0);

        // Blank message rejection
        assertThrows(IllegalArgumentException.class, () -> chatService.postMessage(room.getId(), student.getId(), ""));
        assertThrows(IllegalArgumentException.class, () -> chatService.postMessage(room.getId(), student.getId(), "   "));

        // Oversized message rejection (> 2000 chars)
        String oversized = "x".repeat(2005);
        assertThrows(IllegalArgumentException.class, () -> chatService.postMessage(room.getId(), student.getId(), oversized));

        // XSS sanitization: scripts and unsafe HTML are neutralized
        String payload = "<script>alert('XSS')</script><b>BoldText</b>";
        Message safeMsg = chatService.postMessage(room.getId(), student.getId(), payload);
        assertFalse(safeMsg.getContent().contains("<script>"));
        assertTrue(safeMsg.getContent().contains("&lt;script&gt;"));
    }

    @Test
    @Order(5)
    void testRejectSpoofedSenderAndRequireActiveSession() throws Exception {
        ChatServlet chatServlet = new ChatServlet();

        // 1. Attempt POST to /messages without active session
        HttpServletRequest reqNoSession = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getSession".equals(name)) return null; // No session
                    if ("getParameter".equals(name) && args != null) {
                        if ("roomId".equals(args[0])) return "1";
                        if ("content".equals(args[0])) return "Spoofed message";
                        if ("studentId".equals(args[0])) return "999"; // Spoofed form parameter!
                    }
                    if ("getHeader".equals(name) && "Accept".equals(args[0])) return "application/json";
                    return null;
                }
        );

        final int[] status = new int[1];
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatus".equals(name) && args != null) status[0] = (int) args[0];
                    if ("getWriter".equals(name)) return pw;
                    return null;
                }
        );

        chatServlet.service(reqNoSession, resp);

        // Server MUST return 401 Unauthorized and ignore the spoofed studentId
        assertEquals(401, status[0], "ChatServlet must reject unauthenticated message POSTs");
        assertTrue(sw.toString().contains("Unauthorized"));
    }

    @Test
    @Order(6)
    void testMessageApiServletReturnsValidJson() throws Exception {
        Student student = studentDAO.findOrCreateByNickname("ApiTester");
        Room room = roomDAO.findStudyRooms().get(0);
        chatService.postMessage(room.getId(), student.getId(), "API polling JSON test message");

        MessageApiServlet apiServlet = new MessageApiServlet();

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "GET";
                    if ("getParameter".equals(name) && args != null) {
                        if ("roomId".equals(args[0])) return String.valueOf(room.getId());
                        if ("afterId".equals(args[0])) return "0";
                    }
                    return null;
                }
        );

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        final String[] contentType = new String[1];

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setContentType".equals(name) && args != null) contentType[0] = (String) args[0];
                    if ("getWriter".equals(name)) return pw;
                    return null;
                }
        );

        apiServlet.service(req, resp);

        // Verify valid JSON array response and correct content type
        assertTrue(contentType[0] != null && contentType[0].contains("application/json"));
        String jsonOutput = sw.toString();
        assertTrue(jsonOutput.startsWith("[") && jsonOutput.endsWith("]"), "API response must be a JSON array");
        assertTrue(jsonOutput.contains("senderNickname"));
        assertTrue(jsonOutput.contains("API polling JSON test message"));
    }
}

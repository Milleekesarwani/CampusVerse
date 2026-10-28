package com.campusverse;

import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Message;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.ChatService;
import com.campusverse.service.GhostRoomService;
import com.campusverse.servlet.ChatServlet;
import com.campusverse.util.DatabaseManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase5GhostRoomsTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_phase5_test";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static GhostRoomService ghostRoomService;
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
        ghostRoomService = new GhostRoomService(roomDAO, Clock.systemUTC());
    }

    @Test
    @Order(1)
    void testSupportedDurationsAndUtcTimestampAccuracy() throws SQLException {
        Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");
        Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        GhostRoomService customService = new GhostRoomService(roomDAO, fixedClock);

        // 1. 5 minutes duration
        Room r5m = customService.createGhostRoom("Quick 5m Room", "5 minutes");
        assertEquals("GHOST", r5m.getType());
        assertEquals("ACTIVE", r5m.getStatus());
        assertEquals(baseTime.plus(Duration.ofMinutes(5)), r5m.getExpiresAt().toInstant());

        // 2. 1 hour duration
        Room r1h = customService.createGhostRoom("Study 1h Room", "1 hour");
        assertEquals(baseTime.plus(Duration.ofHours(1)), r1h.getExpiresAt().toInstant());

        // 3. 6 hours duration
        Room r6h = customService.createGhostRoom("Sprint 6h Room", "6 hours");
        assertEquals(baseTime.plus(Duration.ofHours(6)), r6h.getExpiresAt().toInstant());

        // 4. 24 hours duration
        Room r24h = customService.createGhostRoom("Hackathon 24h Room", "24 hours");
        assertEquals(baseTime.plus(Duration.ofHours(24)), r24h.getExpiresAt().toInstant());
    }

    @Test
    @Order(2)
    void testGhostRoomCreationAndActiveMessagingTwoSessions() throws SQLException {
        Student studentA = studentDAO.findOrCreateByNickname("GhostSessionUserA");
        Student studentB = studentDAO.findOrCreateByNickname("GhostSessionUserB");

        // Create 'Java Exam Discussion' with 5-minute duration
        Room room = ghostRoomService.createGhostRoom("Java Exam Discussion", "5 minutes");
        assertNotNull(room.getId());
        assertEquals("ACTIVE", room.getStatus());

        // Send messages from two different sessions while active
        Message m1 = chatService.postMessage(room.getId(), studentA.getId(), "Let's review threads and race conditions.");
        Message m2 = chatService.postMessage(room.getId(), studentB.getId(), "Synchronized blocks and locks are key!");

        assertNotNull(m1.getId());
        assertNotNull(m2.getId());

        List<Message> history = chatService.getRecentMessages(room.getId(), 50);
        assertEquals(2, history.size());
        assertEquals("GhostSessionUserA", history.get(0).getSenderNickname());
        assertEquals("GhostSessionUserB", history.get(1).getSenderNickname());
    }

    @Test
    @Order(3)
    void testExpiryBoundaryAndRejectionLogicWithClock() throws SQLException {
        Instant startTime = Instant.parse("2026-10-28T12:00:00Z");
        Clock testClock = Clock.fixed(startTime, ZoneOffset.UTC);
        GhostRoomService testGhostService = new GhostRoomService(roomDAO, testClock);
        ChatService activeChatService = new ChatService(messageDAO, roomDAO, testClock);

        Student student = studentDAO.findOrCreateByNickname("BoundaryStudent");

        // Create room expiring at 12:05:00Z
        Room room = testGhostService.createGhostRoom("Boundary Sprint Room", "5 minutes");
        Instant expiryTime = room.getExpiresAt().toInstant();
        assertEquals(startTime.plus(Duration.ofMinutes(5)), expiryTime);

        // At 12:04:59Z (1 second before expiry) -> Active
        Clock preExpiryClock = Clock.fixed(expiryTime.minusSeconds(1), ZoneOffset.UTC);
        GhostRoomService preExpiryService = new GhostRoomService(roomDAO, preExpiryClock);
        assertFalse(preExpiryService.isRoomExpired(room), "Room must be active right before expiration timestamp");

        // At 12:05:01Z (1 second after expiry) -> Expired
        Clock postExpiryClock = Clock.fixed(expiryTime.plusSeconds(1), ZoneOffset.UTC);
        GhostRoomService postExpiryService = new GhostRoomService(roomDAO, postExpiryClock);
        ChatService postExpiryChatService = new ChatService(messageDAO, roomDAO, postExpiryClock);

        assertTrue(postExpiryService.isRoomExpired(room), "Room must be expired after expiration timestamp");

        // Verification: postMessage throws IllegalStateException after expiration
        assertThrows(IllegalStateException.class, () -> {
            postExpiryChatService.postMessage(room.getId(), student.getId(), "Blocked message attempt");
        });
    }

    @Test
    @Order(4)
    void testDirectHttpPostRejectionWhenRoomExpired() throws Exception {
        Instant initialTime = Instant.parse("2026-10-28T08:00:00Z");
        Clock creationClock = Clock.fixed(initialTime, ZoneOffset.UTC);
        GhostRoomService service = new GhostRoomService(roomDAO, creationClock);

        Student student = studentDAO.findOrCreateByNickname("ClientBypassTester");

        // Room expired in the past
        Room room = service.createGhostRoom("Expired Target Room", "5 minutes");
        // Mark room closed in DB
        roomDAO.updateStatus(room.getId(), "CLOSED");

        ChatServlet servlet = new ChatServlet();

        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName()) && "currentStudent".equals(args[0])) return student;
                    return null;
                }
        );

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getSession".equals(name)) return session;
                    if ("getParameter".equals(name) && args != null) {
                        if ("roomId".equals(args[0])) return String.valueOf(room.getId());
                        if ("content".equals(args[0])) return "Trying to bypass UI countdown";
                    }
                    if ("getHeader".equals(name) && "Accept".equals(args[0])) return "application/json";
                    return null;
                }
        );

        final int[] responseStatus = new int[1];
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("setStatus".equals(methodName) && args != null) responseStatus[0] = (int) args[0];
                    if ("getWriter".equals(methodName)) return pw;
                    return null;
                }
        );

        servlet.service(req, resp);

        // Backend must reject with 403 Forbidden!
        assertEquals(HttpServletResponse.SC_FORBIDDEN, responseStatus[0], "Backend must reject POST to expired room with 403 Forbidden");
    }

    @Test
    @Order(5)
    void testHistoricalMessagesRetainedAfterExpiry() throws SQLException {
        Instant startTime = Instant.parse("2026-10-28T09:00:00Z");
        Clock activeClock = Clock.fixed(startTime, ZoneOffset.UTC);
        GhostRoomService gService = new GhostRoomService(roomDAO, activeClock);
        ChatService activeChat = new ChatService(messageDAO, roomDAO, activeClock);

        Student student = studentDAO.findOrCreateByNickname("ArchiveTester");

        Room room = gService.createGhostRoom("Discussion To Archive", "5 minutes");

        // Send 2 messages during active window
        activeChat.postMessage(room.getId(), student.getId(), "Important archive insight 1");
        activeChat.postMessage(room.getId(), student.getId(), "Important archive insight 2");

        // Transition to expired state
        Clock expiredClock = Clock.fixed(startTime.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        ChatService expiredChat = new ChatService(messageDAO, roomDAO, expiredClock);

        // Verify history remains completely intact for demo viewing
        List<Message> history = expiredChat.getRecentMessages(room.getId(), 50);
        assertEquals(2, history.size());
        assertEquals("Important archive insight 1", history.get(0).getContent());
        assertEquals("Important archive insight 2", history.get(1).getContent());
    }
}

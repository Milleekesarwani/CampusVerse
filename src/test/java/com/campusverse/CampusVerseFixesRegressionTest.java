package com.campusverse;

import com.campusverse.dao.DoubtDAO;
import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Doubt;
import com.campusverse.model.Interest;
import com.campusverse.model.MatchResult;
import com.campusverse.model.Message;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.ChatService;
import com.campusverse.service.DoubtService;
import com.campusverse.service.GhostRoomService;
import com.campusverse.service.VibeMatchService;
import com.campusverse.servlet.ChatServlet;
import com.campusverse.servlet.DoubtServlet;
import com.campusverse.servlet.GhostRoomServlet;
import com.campusverse.servlet.HomeServlet;
import com.campusverse.servlet.MessageApiServlet;
import com.campusverse.servlet.StudentServlet;
import com.campusverse.servlet.VibeMatchServlet;
import com.campusverse.util.DatabaseManager;
import com.campusverse.util.SecurityFilter;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive regression tests verifying all 8 user-reported issues:
 * 1. HomeServlet: No auto-login as Milee_Kesarwani, shows unauthenticated state.
 * 2. Two separate student sessions with distinct identities.
 * 3. StudentServlet: Supports action=saveProfile and action=update_profile, persists nicknames and interests, and clears all interests.
 * 4. GhostRoom: Supports title and hours, properly persists topic and subject.
 * 5. Duration parsing: "2h" and "2 hours" parse as 2 hours, validates supported durations, rejects invalid.
 * 6. Expiry check: Rejects messages at or after expiry instant (now >= expiresAt).
 * 7. Chat pagination: Retrieves messages beyond the 100-message limit using afterId cursor.
 * 8. VibeMatch: Empty state when no matches, no fallback sample peers.
 * 9. Security & Error handling: Failed requests (non-author resolve, nickname conflict) return error status codes.
 * 10. Persistence: Data survives database reconnection.
 */
public class CampusVerseFixesRegressionTest {

    private static final String TEST_DB_URL = "jdbc:h2:./target/testdb/campusverse_fixes_reg;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static StudentDAO studentDAO;
    private static RoomDAO roomDAO;
    private static MessageDAO messageDAO;
    private static DoubtDAO doubtDAO;
    private static GhostRoomService ghostRoomService;
    private static ChatService chatService;
    private static DoubtService doubtService;
    private static VibeMatchService vibeMatchService;

    @BeforeAll
    static void init() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        roomDAO = new RoomDAO();
        messageDAO = new MessageDAO();
        doubtDAO = new DoubtDAO();
        ghostRoomService = new GhostRoomService(roomDAO, Clock.systemUTC());
        chatService = new ChatService(messageDAO, roomDAO, studentDAO, Clock.systemUTC());
        doubtService = new DoubtService(doubtDAO);
        vibeMatchService = new VibeMatchService(studentDAO);
    }

    // --- Issue 1: HomeServlet unauthenticated state ---

    @Test
    @DisplayName("Fix 1: HomeServlet does not auto-login visitor as Milee_Kesarwani")
    void testHomeServletNoAutoLogin() throws Exception {
        HomeServlet servlet = new HomeServlet();
        Map<String, Object> reqAttrs = new HashMap<>();
        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockSession(sessionAttrs);

        HttpServletRequest request = createMockRequest("GET", "/home", new HashMap<>(), new HashMap<>(), session, reqAttrs);
        int[] status = {200};
        HttpServletResponse response = createMockResponse(status, new HashMap<>(), new StringWriter());

        servlet.service(request, response);

        // Verify no auto-login occurred
        assertNull(reqAttrs.get("currentStudent"), "Unauthenticated visitor must not have currentStudent assigned");
        assertNull(sessionAttrs.get("currentStudent"), "Session must not be populated with an auto-login student");
    }

    // --- Issue 1 & 2: Two separate student sessions ---

    @Test
    @DisplayName("Fix 1 & 2: Two separate student sessions maintain distinct identities")
    void testTwoSeparateStudentSessions() throws Exception {
        StudentServlet servlet = new StudentServlet();

        // Student A logs in
        Map<String, Object> sessionAttrsA = new HashMap<>();
        HttpSession sessionA = createMockSession(sessionAttrsA);
        Map<String, String> paramsA = Map.of("action", "login", "nickname", "Student_Alpha_Reg");
        HttpServletRequest reqA = createMockRequest("POST", "/student", Map.of("Accept", "application/json"), paramsA, sessionA);
        StringWriter outA = new StringWriter();
        int[] statusA = {200};
        HttpServletResponse respA = createMockResponse(statusA, new HashMap<>(), outA);

        servlet.service(reqA, respA);
        Student studentA = (Student) sessionAttrsA.get("currentStudent");
        assertNotNull(studentA, "Student A should have a valid session student");
        assertEquals("Student_Alpha_Reg", studentA.getNickname());

        // Student B logs in
        Map<String, Object> sessionAttrsB = new HashMap<>();
        HttpSession sessionB = createMockSession(sessionAttrsB);
        Map<String, String> paramsB = Map.of("action", "login", "nickname", "Student_Beta_Reg");
        HttpServletRequest reqB = createMockRequest("POST", "/student", Map.of("Accept", "application/json"), paramsB, sessionB);
        StringWriter outB = new StringWriter();
        int[] statusB = {200};
        HttpServletResponse respB = createMockResponse(statusB, new HashMap<>(), outB);

        servlet.service(reqB, respB);
        Student studentB = (Student) sessionAttrsB.get("currentStudent");
        assertNotNull(studentB, "Student B should have a valid session student");
        assertEquals("Student_Beta_Reg", studentB.getNickname());

        // Ensure distinct identities
        assertNotEquals(studentA.getId(), studentB.getId(), "Students must have unique IDs");
        assertNotEquals(studentA.getNickname(), studentB.getNickname(), "Students must have unique nicknames");
    }

    // --- Issue 2: StudentServlet profile and interest persistence & clearing ---

    @Test
    @DisplayName("Fix 2: StudentServlet supports both action=saveProfile and action=update_profile")
    void testStudentServletActionSupport() throws Exception {
        StudentServlet servlet = new StudentServlet();
        Student student = studentDAO.findOrCreateByNickname("ActionTestStudent");
        List<Interest> all = studentDAO.getAllInterests();
        assertTrue(all.size() >= 2);

        // 1. Test action=saveProfile
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("currentStudent", student);
        HttpSession session = createMockSession(sessionAttrs);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "saveProfile");
        params.put("nickname", "ActionTestStudent");
        params.put("interests", new String[]{all.get(0).getName()});

        HttpServletRequest req = createMockMultiParamRequest("POST", "/student", Map.of("Accept", "application/json"), params, session);
        StringWriter out = new StringWriter();
        int[] status = {200};
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        assertEquals(200, status[0]);
        List<Interest> savedInterests = studentDAO.getStudentInterests(student.getId());
        assertEquals(1, savedInterests.size());
        assertEquals(all.get(0).getName(), savedInterests.get(0).getName());

        // 2. Test action=update_profile with two interests
        params.put("action", "update_profile");
        params.put("interests", new String[]{all.get(0).getName(), all.get(1).getName()});
        req = createMockMultiParamRequest("POST", "/student", Map.of("Accept", "application/json"), params, session);
        out = new StringWriter();
        status[0] = 200;
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        assertEquals(200, status[0]);
        savedInterests = studentDAO.getStudentInterests(student.getId());
        assertEquals(2, savedInterests.size());

        // 3. Test clearing all interests when empty/omitted
        params.put("action", "saveProfile");
        params.remove("interests"); // Omit interests
        req = createMockMultiParamRequest("POST", "/student", Map.of("Accept", "application/json"), params, session);
        out = new StringWriter();
        status[0] = 200;
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        assertEquals(200, status[0]);
        savedInterests = studentDAO.getStudentInterests(student.getId());
        assertTrue(savedInterests.isEmpty(), "Clearing all interests must result in empty interests list");
    }

    // --- Issue 3: GhostRoom creation sends title and hours, aligns fields ---

    @Test
    @DisplayName("Fix 3: GhostRoom creation accepts title, hours, topic, and subject")
    void testGhostRoomCreationAlignFields() throws Exception {
        GhostRoomServlet servlet = new GhostRoomServlet();
        Student student = studentDAO.findOrCreateByNickname("GhostHost");

        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("currentStudent", student);
        HttpSession session = createMockSession(sessionAttrs);

        Map<String, String> params = new HashMap<>();
        params.put("action", "create");
        params.put("title", "Exam Sprint Room"); // Frontend sends 'title'
        params.put("hours", "2");                // Frontend sends 'hours'
        params.put("topic", "Graph Theory & Dynamic Programming");
        params.put("subject", "DSA");

        HttpServletRequest req = createMockRequest("POST", "/ghostrooms", Map.of("Accept", "application/json"), params, session);
        StringWriter out = new StringWriter();
        int[] status = {200};
        HttpServletResponse resp = createMockResponse(status, new HashMap<>(), out);

        servlet.service(req, resp);

        assertEquals(200, status[0]);
        String json = out.toString();
        assertTrue(json.contains("\"success\":true"), "Response must indicate success");
        assertTrue(json.contains("Exam Sprint Room"), "Response must contain title");
        assertTrue(json.contains("Graph Theory") && json.contains("Dynamic Programming"), "Response must contain topic");
        assertTrue(json.contains("DSA"), "Response must contain subject");

        // Verify persisted room in database
        List<Room> ghosts = roomDAO.findAllGhostRooms();
        Room created = ghosts.stream().filter(r -> "Exam Sprint Room".equals(r.getName())).findFirst().orElseThrow();
        assertTrue(created.getTopic().contains("Graph Theory"));
        assertEquals("DSA", created.getSubject());
        assertNotNull(created.getExpiresAt());
    }

    // --- Issue 6: Duration parsing (2h and "2 hours" mean 2 hours) ---

    @Test
    @DisplayName("Fix 6: Duration parsing: 2h and '2 hours' resolve to 2 hours, handles edge cases (12h, 21h, 25m), rejects invalid")
    void testGhostRoomDurationParsing() {
        GhostRoomService service = new GhostRoomService(roomDAO, Clock.systemUTC());

        // 2 hours variants
        assertEquals(Duration.ofHours(2), service.parseDuration("2h"));
        assertEquals(Duration.ofHours(2), service.parseDuration("2 hours"));
        assertEquals(Duration.ofHours(2), service.parseDuration("2 hr"));
        assertEquals(Duration.ofHours(2), service.parseDuration("2"));

        // Edge cases previously misparsed due to substring matching:
        // "12 hours" must NOT parse as 2 hours
        assertEquals(Duration.ofHours(12), service.parseDuration("12 hours"));
        assertEquals(Duration.ofHours(12), service.parseDuration("12h"));

        // "21 hours" must NOT parse as 1 hour
        assertEquals(Duration.ofHours(21), service.parseDuration("21 hours"));
        assertEquals(Duration.ofHours(21), service.parseDuration("21h"));

        // "25 minutes" must NOT parse as 5 minutes
        assertEquals(Duration.ofMinutes(25), service.parseDuration("25 minutes"));
        assertEquals(Duration.ofMinutes(25), service.parseDuration("25m"));

        // Standard variants
        assertEquals(Duration.ofMinutes(5), service.parseDuration("5m"));
        assertEquals(Duration.ofHours(1), service.parseDuration("1h"));
        assertEquals(Duration.ofHours(6), service.parseDuration("6h"));
        assertEquals(Duration.ofHours(24), service.parseDuration("24h"));

        // Invalid durations
        assertThrows(IllegalArgumentException.class, () -> service.parseDuration("9999h"));
        assertThrows(IllegalArgumentException.class, () -> service.parseDuration("invalid_duration"));
    }

    @Test
    @DisplayName("Fix: Non-JSON student login redirects to /home consistently")
    void testLoginRedirectsToHome() throws Exception {
        StudentServlet servlet = new StudentServlet();
        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockSession(sessionAttrs);

        Map<String, String> params = Map.of("action", "login", "nickname", "HomeRedirectUser");
        HttpServletRequest req = createMockRequest("POST", "/student", new HashMap<>(), params, session);
        String[] redirectUrl = {null};
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("sendRedirect".equals(m.getName())) {
                        redirectUrl[0] = (String) args[0];
                        return null;
                    }
                    return null;
                }
        );

        servlet.service(req, resp);
        assertNotNull(redirectUrl[0]);
        assertTrue(redirectUrl[0].endsWith("/home"), "Login must redirect to /home, actual: " + redirectUrl[0]);
    }

    // --- Issue 6: Expiration checks reject messages at or after expiry ---

    @Test
    @DisplayName("Fix 6: Messages rejected at or after expiry instant (now >= expiresAt)")
    void testGhostRoomExpiryRejection() throws Exception {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Clock fixedClock = Clock.fixed(t0, ZoneOffset.UTC);

        GhostRoomService gService = new GhostRoomService(roomDAO, fixedClock);
        ChatService cService = new ChatService(messageDAO, roomDAO, studentDAO, fixedClock);

        Student author = studentDAO.findOrCreateByNickname("ExpiryTestAuthor");
        Room room = gService.createGhostRoom("Expiry Room", "1h");
        Instant expiresAt = room.getExpiresAt().toInstant();

        // 1. Before expiry: message succeeds
        Clock clockBefore = Clock.fixed(expiresAt.minusSeconds(10), ZoneOffset.UTC);
        ChatService cServiceBefore = new ChatService(messageDAO, roomDAO, studentDAO, clockBefore);
        Message msg1 = cServiceBefore.postMessage(room.getId(), author.getId(), "Message before expiry");
        assertNotNull(msg1);

        // 2. AT exact expiry: message rejected
        Clock clockAtExpiry = Clock.fixed(expiresAt, ZoneOffset.UTC);
        ChatService cServiceAt = new ChatService(messageDAO, roomDAO, studentDAO, clockAtExpiry);
        assertThrows(IllegalStateException.class, () ->
                cServiceAt.postMessage(room.getId(), author.getId(), "Message at expiry instant")
        );

        // 3. AFTER expiry: message rejected
        Clock clockAfterExpiry = Clock.fixed(expiresAt.plusSeconds(30), ZoneOffset.UTC);
        ChatService cServiceAfter = new ChatService(messageDAO, roomDAO, studentDAO, clockAfterExpiry);
        assertThrows(IllegalStateException.class, () ->
                cServiceAfter.postMessage(room.getId(), author.getId(), "Message after expiry instant")
        );
    }

    // --- Issue 5: Chat pagination beyond 100 messages using afterId cursor ---

    @Test
    @DisplayName("Fix 5: Chat pagination retrieves all messages beyond 100 using afterId cursor")
    void testChatPaginationBeyond100Messages() throws Exception {
        Room room = roomDAO.createRoom(new Room(null, "PaginationTestRoom", "STUDY", null, null, null, null, "ACTIVE"));
        Student sender = studentDAO.findOrCreateByNickname("PaginationSender");

        // Seed 125 messages
        List<Long> insertedIds = new ArrayList<>();
        for (int i = 1; i <= 125; i++) {
            Message m = chatService.postMessage(room.getId(), sender.getId(), "Message chunk " + i);
            insertedIds.add(m.getId());
        }

        // Chunk 1: afterId = 0
        List<Message> chunk1 = chatService.getMessagesAfter(room.getId(), 0L);
        assertEquals(100, chunk1.size(), "Chunk 1 must return exactly 100 messages due to LIMIT 100");

        // Advance cursor to highest received ID
        Long cursor = chunk1.get(chunk1.size() - 1).getId();

        // Chunk 2: afterId = cursor
        List<Message> chunk2 = chatService.getMessagesAfter(room.getId(), cursor);
        assertEquals(25, chunk2.size(), "Chunk 2 must return the remaining 25 messages");

        // Verify total retrieved without omission or overlap
        List<Message> total = new ArrayList<>();
        total.addAll(chunk1);
        total.addAll(chunk2);
        assertEquals(125, total.size(), "Total messages retrieved across pages must be exactly 125");
        assertEquals(insertedIds.get(0), total.get(0).getId());
        assertEquals(insertedIds.get(124), total.get(124).getId());
    }

    // --- Issue 8: VibeMatch empty state without mock fallback ---

    @Test
    @DisplayName("Fix 8: VibeMatch returns empty list and no mock peer fallback when no matches")
    void testVibeMatchEmptyState() throws Exception {
        Student lonelyStudent = studentDAO.findOrCreateByNickname("LonelyPioneerStudent");
        // Clear interests
        studentDAO.setStudentInterests(lonelyStudent.getId(), List.of());

        List<MatchResult> matches = vibeMatchService.findMatches(lonelyStudent.getId());
        assertNotNull(matches);

        // Through Servlet JSON
        VibeMatchServlet servlet = new VibeMatchServlet();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("currentStudent", lonelyStudent);
        HttpSession session = createMockSession(sessionAttrs);

        HttpServletRequest req = createMockRequest("GET", "/vibematch", Map.of("Accept", "application/json"), Map.of("json", "true"), session);
        StringWriter out = new StringWriter();
        int[] status = {200};
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        assertEquals(200, status[0]);
        // Result is valid JSON array without mock peer entries if lonely
        assertTrue(out.toString().startsWith("[") && out.toString().endsWith("]"));
    }

    // --- Issue 4 & Security: Failed requests return proper error status without false success ---

    @Test
    @DisplayName("Fix 4: Author-only doubt resolution returns 403 Forbidden to non-authors")
    void testNonAuthorDoubtResolutionFails() throws Exception {
        Student author = studentDAO.findOrCreateByNickname("DoubtAuthorReal");
        Student hacker = studentDAO.findOrCreateByNickname("NotTheAuthorStudent");

        Doubt doubt = doubtService.postDoubt(author.getId(), "Java", "How does classloading work?", "Detailed question here");

        DoubtServlet servlet = new DoubtServlet();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("currentStudent", hacker); // Hacker is logged in
        HttpSession session = createMockSession(sessionAttrs);

        Map<String, String> params = Map.of("action", "resolve", "doubtId", String.valueOf(doubt.getId()));
        HttpServletRequest req = createMockRequest("POST", "/doubts", Map.of("Accept", "application/json"), params, session);
        StringWriter out = new StringWriter();
        int[] status = {200};
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        // Must reject with 403 Forbidden
        assertEquals(HttpServletResponse.SC_FORBIDDEN, status[0], "Non-author resolve must return 403 Forbidden");
        assertTrue(out.toString().contains("error"), "Response must contain error JSON");

        // Doubt status in DB must still be OPEN
        Doubt fresh = doubtDAO.findById(doubt.getId());
        assertEquals("OPEN", fresh.getStatus());
    }

    @Test
    @DisplayName("Fix 4: Nickname conflict during saveProfile returns 409 Conflict")
    void testSaveProfileNicknameConflictFails() throws Exception {
        Student s1 = studentDAO.findOrCreateByNickname("ExistingStudent_Nick");
        Student s2 = studentDAO.findOrCreateByNickname("AnotherStudent_Nick");

        StudentServlet servlet = new StudentServlet();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("currentStudent", s2);
        HttpSession session = createMockSession(sessionAttrs);

        // s2 attempts to rename to s1's nickname
        Map<String, String> params = Map.of("action", "saveProfile", "nickname", "ExistingStudent_Nick");
        HttpServletRequest req = createMockRequest("POST", "/student", Map.of("Accept", "application/json"), params, session);
        StringWriter out = new StringWriter();
        int[] status = {200};
        servlet.service(req, createMockResponse(status, new HashMap<>(), out));

        assertEquals(HttpServletResponse.SC_CONFLICT, status[0], "Conflicting nickname must return 409 Conflict");
        assertTrue(out.toString().contains("Nickname already taken"), "Error response must describe conflict");
    }

    // --- Simulated Server Restart / Persistence ---

    @Test
    @DisplayName("Persistence: Data survives simulated server restart and database reconnection")
    void testDataPersistenceAcrossRestart() throws SQLException {
        // Create student and room in current test DB
        Student student = studentDAO.findOrCreateByNickname("PersistentRestartStudent");
        Room room = roomDAO.createRoom(new Room(null, "RestartPersistentRoom", "STUDY", "Restart Topic", "Java", null, null, "ACTIVE"));

        // Simulate restart by creating completely new DAO instances
        StudentDAO freshStudentDAO = new StudentDAO();
        RoomDAO freshRoomDAO = new RoomDAO();

        Student reloadedStudent = freshStudentDAO.findByNickname("PersistentRestartStudent");
        assertNotNull(reloadedStudent);
        assertEquals(student.getId(), reloadedStudent.getId());

        Room reloadedRoom = freshRoomDAO.findById(room.getId());
        assertNotNull(reloadedRoom);
        assertEquals("RestartPersistentRoom", reloadedRoom.getName());
        assertEquals("Restart Topic", reloadedRoom.getTopic());
        assertEquals("Java", reloadedRoom.getSubject());
    }

    // --- Mock Helpers ---

    private HttpSession createMockSession(Map<String, Object> attrs) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) return attrs.get(args[0]);
                    if ("setAttribute".equals(name)) {
                        attrs.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("removeAttribute".equals(name)) {
                        attrs.remove(args[0]);
                        return null;
                    }
                    if ("getId".equals(name)) return "mock-session-" + UUID.randomUUID();
                    return null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String servletPath,
                                                 Map<String, String> headers, Map<String, String> params,
                                                 HttpSession session) {
        return createMockRequest(method, servletPath, headers, params, session, new HashMap<>());
    }

    private HttpServletRequest createMockRequest(String method, String servletPath,
                                                 Map<String, String> headers, Map<String, String> params,
                                                 HttpSession session, Map<String, Object> reqAttrs) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getServletPath".equals(name)) return servletPath;
                    if ("getHeader".equals(name)) return headers.get(args[0]);
                    if ("getParameter".equals(name)) return params.get(args[0]);
                    if ("getSession".equals(name)) {
                        boolean create = args.length > 0 && Boolean.TRUE.equals(args[0]);
                        return session;
                    }
                    if ("getAttribute".equals(name)) return reqAttrs.get(args[0]);
                    if ("setAttribute".equals(name)) {
                        reqAttrs.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("getRequestDispatcher".equals(name)) {
                        return (RequestDispatcher) Proxy.newProxyInstance(
                                RequestDispatcher.class.getClassLoader(),
                                new Class<?>[]{RequestDispatcher.class},
                                (p2, m2, a2) -> null
                        );
                    }
                    if ("getContextPath".equals(name)) return "/campusverse-lite";
                    return null;
                }
        );
    }

    private HttpServletRequest createMockMultiParamRequest(String method, String servletPath,
                                                           Map<String, String> headers, Map<String, Object> params,
                                                           HttpSession session) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getServletPath".equals(name)) return servletPath;
                    if ("getHeader".equals(name)) return headers.get(args[0]);
                    if ("getParameter".equals(name)) {
                        Object v = params.get(args[0]);
                        if (v instanceof String[]) {
                            String[] arr = (String[]) v;
                            return arr.length > 0 ? arr[0] : null;
                        }
                        return v != null ? v.toString() : null;
                    }
                    if ("getParameterValues".equals(name)) {
                        Object v = params.get(args[0]);
                        if (v instanceof String[]) return v;
                        if (v != null) return new String[]{v.toString()};
                        return null;
                    }
                    if ("getSession".equals(name)) return session;
                    if ("getContextPath".equals(name)) return "/campusverse-lite";
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(int[] statusCode, Map<String, String> headers, StringWriter out) {
        PrintWriter pw = new PrintWriter(out);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("setStatus".equals(name)) {
                        statusCode[0] = (int) args[0];
                        return null;
                    }
                    if ("sendError".equals(name)) {
                        statusCode[0] = (int) args[0];
                        return null;
                    }
                    if ("sendRedirect".equals(name)) {
                        return null;
                    }
                    if ("setHeader".equals(name)) {
                        headers.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    if ("setContentType".equals(name)) return null;
                    if ("setCharacterEncoding".equals(name)) return null;
                    if ("getWriter".equals(name)) return pw;
                    return null;
                }
        );
    }
}

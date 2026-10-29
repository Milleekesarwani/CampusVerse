package com.campusverse;

import com.campusverse.dao.DoubtDAO;
import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Doubt;
import com.campusverse.model.MatchResult;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.ChatService;
import com.campusverse.service.DoubtService;
import com.campusverse.service.GhostRoomService;
import com.campusverse.service.VibeMatchService;
import com.campusverse.servlet.MessageApiServlet;
import com.campusverse.util.DatabaseManager;
import com.campusverse.util.SecurityFilter;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.FilterChain;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityAndIntegrationRegressionTest {

    private static final String TEST_DB_URL = "jdbc:h2:./target/testdb/campusverse_sec_reg;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static StudentDAO studentDAO;
    private static RoomDAO roomDAO;
    private static MessageDAO messageDAO;
    private static DoubtDAO doubtDAO;
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

        chatService = new ChatService(messageDAO, roomDAO);
        doubtService = new DoubtService(doubtDAO);
        vibeMatchService = new VibeMatchService(studentDAO);
    }

    @Test
    @DisplayName("SecurityFilter: POST without CSRF token is rejected with 403")
    void testSecurityFilterRejectsPostWithoutCsrfToken() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        String sessionToken = UUID.randomUUID().toString();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(SecurityFilter.CSRF_TOKEN_ATTR, sessionToken);
        sessionAttrs.put("currentStudent", new Student(1L, "testUser", null));

        HttpSession session = createMockSession(sessionAttrs);
        Map<String, String> headers = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        params.put("content", "hello");

        int[] statusCode = new int[1];
        HttpServletRequest req = createMockRequest("POST", "/messages", null, headers, params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>());

        boolean[] chainCalled = new boolean[1];
        FilterChain chain = (r, res) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertFalse(chainCalled[0], "Chain should NOT be called for missing CSRF token");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, statusCode[0], "Should return 403 Forbidden");
    }

    @Test
    @DisplayName("SecurityFilter: POST with invalid CSRF token is rejected with 403")
    void testSecurityFilterRejectsPostWithInvalidCsrfToken() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        String sessionToken = UUID.randomUUID().toString();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(SecurityFilter.CSRF_TOKEN_ATTR, sessionToken);
        sessionAttrs.put("currentStudent", new Student(1L, "testUser", null));

        HttpSession session = createMockSession(sessionAttrs);
        Map<String, String> headers = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        params.put("csrfToken", "wrong-csrf-token");

        int[] statusCode = new int[1];
        HttpServletRequest req = createMockRequest("POST", "/messages", null, headers, params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>());

        boolean[] chainCalled = new boolean[1];
        FilterChain chain = (r, res) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertFalse(chainCalled[0]);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, statusCode[0]);
    }

    @Test
    @DisplayName("SecurityFilter: POST with valid CSRF token in parameter passes")
    void testSecurityFilterAllowsPostWithValidParameterToken() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        String validToken = UUID.randomUUID().toString();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(SecurityFilter.CSRF_TOKEN_ATTR, validToken);
        sessionAttrs.put("currentStudent", new Student(1L, "testUser", null));

        HttpSession session = createMockSession(sessionAttrs);
        Map<String, String> headers = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        params.put("csrfToken", validToken);

        int[] statusCode = new int[1];
        HttpServletRequest req = createMockRequest("POST", "/messages", null, headers, params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>());

        boolean[] chainCalled = new boolean[1];
        FilterChain chain = (r, res) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled[0], "Chain should proceed when valid parameter CSRF token is present");
    }

    @Test
    @DisplayName("SecurityFilter: POST with valid CSRF token in header passes")
    void testSecurityFilterAllowsPostWithValidHeaderToken() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        String validToken = UUID.randomUUID().toString();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(SecurityFilter.CSRF_TOKEN_ATTR, validToken);
        sessionAttrs.put("currentStudent", new Student(1L, "testUser", null));

        HttpSession session = createMockSession(sessionAttrs);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-CSRF-Token", validToken);
        Map<String, String> params = new HashMap<>();

        int[] statusCode = new int[1];
        HttpServletRequest req = createMockRequest("POST", "/messages", null, headers, params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>());

        boolean[] chainCalled = new boolean[1];
        FilterChain chain = (r, res) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled[0], "Chain should proceed when valid header CSRF token is present");
    }

    @Test
    @DisplayName("SecurityFilter: Initial unauthenticated nickname login is exempt from CSRF rejection")
    void testSecurityFilterExemptsInitialLogin() throws Exception {
        SecurityFilter filter = new SecurityFilter();

        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockSession(sessionAttrs);
        Map<String, String> headers = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        params.put("action", "login");
        params.put("nickname", "NewStudent");

        int[] statusCode = new int[1];
        HttpServletRequest req = createMockRequest("POST", "/student", null, headers, params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>());

        boolean[] chainCalled = new boolean[1];
        FilterChain chain = (r, res) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled[0], "Initial login POST should be permitted to establish session");
    }

    @Test
    @DisplayName("MessageApiServlet: Rejects unauthenticated requests with HTTP 401")
    void testMessageApiServletRejectsUnauthenticated() throws Exception {
        MessageApiServlet servlet = new MessageApiServlet();

        // No currentStudent in session
        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockSession(sessionAttrs);

        Map<String, String> params = new HashMap<>();
        params.put("roomId", "1");

        int[] statusCode = new int[]{200};
        StringWriter out = new StringWriter();
        HttpServletRequest req = createMockRequest("GET", "/api/messages", out, new HashMap<>(), params, session);
        HttpServletResponse resp = createMockResponse(statusCode, new HashMap<>(), out);

        servlet.service(req, resp);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, statusCode[0]);
        assertTrue(out.toString().contains("Unauthorized"));
    }

    @Test
    @DisplayName("SecurityUtils: escapeHtml prevents XSS injection")
    void testSecurityUtilsEscapeHtml() {
        String xss = "<script>alert('XSS')</script>";
        String escaped = SecurityUtils.escapeHtml(xss);
        assertEquals("&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;", escaped);
        assertFalse(escaped.contains("<"));
        assertFalse(escaped.contains(">"));

        String quotes = "\"hello\" & 'world'";
        String escapedQuotes = SecurityUtils.escapeHtml(quotes);
        assertEquals("&quot;hello&quot; &amp; &#x27;world&#x27;", escapedQuotes);
    }

    @Test
    @DisplayName("DoubtDrop: Only author can mark doubt as resolved")
    void testDoubtDropAuthorOnlyResolution() throws SQLException {
        Student author = studentDAO.findOrCreateByNickname("AuthorSecTest");
        Student stranger = studentDAO.findOrCreateByNickname("StrangerSecTest");

        Doubt d = doubtService.postDoubt(author.getId(), "Java", "How does class loading work?", "Can someone explain bootstrap loader?");
        assertNotNull(d.getId());

        // Stranger attempting to resolve throws SecurityException
        assertThrows(SecurityException.class, () -> {
            doubtService.resolveDoubt(d.getId(), stranger.getId());
        });

        // Author can successfully resolve
        boolean resolved = doubtService.resolveDoubt(d.getId(), author.getId());
        assertTrue(resolved);
        Doubt updated = doubtService.getDoubtById(d.getId());
        assertEquals("RESOLVED", updated.getStatus());
    }

    @Test
    @DisplayName("GhostRooms: Expired room strictly rejects message submission")
    void testExpiredGhostRoomRejectsMessage() throws SQLException {
        Instant t0 = Instant.parse("2026-10-28T10:00:00Z");
        Clock clock1 = Clock.fixed(t0, ZoneOffset.UTC);
        GhostRoomService gService = new GhostRoomService(roomDAO, clock1);

        Room room = gService.createGhostRoom("Ephemeral Sprint", "5 minutes");
        assertNotNull(room.getId());

        Student stu = studentDAO.findOrCreateByNickname("GhostTester");

        // Fast forward 6 minutes past expiration
        Clock clock2 = Clock.fixed(t0.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        ChatService lateChat = new ChatService(messageDAO, roomDAO, clock2);

        assertThrows(IllegalStateException.class, () -> {
            lateChat.postMessage(room.getId(), stu.getId(), "Trying to send to expired room");
        });
    }

    @Test
    @DisplayName("VibeMatch: Accurately computes Jaccard Similarity (Intersection / Union)")
    void testVibeMatchJaccardCalculation() throws SQLException {
        Student s1 = studentDAO.findOrCreateByNickname("JaccardUserA");
        Student s2 = studentDAO.findOrCreateByNickname("JaccardUserB");

        List<com.campusverse.model.Interest> all = studentDAO.getAllInterests();
        assertTrue(all.size() >= 4);

        // s1: items 0, 1, 2
        studentDAO.setStudentInterests(s1.getId(), List.of(all.get(0).getId(), all.get(1).getId(), all.get(2).getId()));
        // s2: items 1, 2, 3
        studentDAO.setStudentInterests(s2.getId(), List.of(all.get(1).getId(), all.get(2).getId(), all.get(3).getId()));

        // Intersection: {1, 2} (size 2), Union: {0, 1, 2, 3} (size 4) => 2/4 = 50%
        List<MatchResult> matches = vibeMatchService.findMatches(s1.getId());
        MatchResult target = matches.stream().filter(m -> m.getPeer().getId().equals(s2.getId())).findFirst().orElseThrow();

        assertEquals(50, target.getMatchPercentage());
        assertEquals(2, target.getSharedInterests().size());
    }

    // --- Dynamic Mock Helpers ---

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
                    return null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String servletPath, StringWriter out,
                                                Map<String, String> headers, Map<String, String> params, HttpSession session) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("isSecure".equals(name)) return false;
                    if ("getMethod".equals(name)) return method;
                    if ("getServletPath".equals(name)) return servletPath;
                    if ("getHeader".equals(name)) return headers.get(args[0]);
                    if ("getParameter".equals(name)) return params.get(args[0]);
                    if ("getSession".equals(name)) return session;
                    if ("setCharacterEncoding".equals(name)) return null;
                    if ("setAttribute".equals(name)) return null;
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(int[] statusCode, Map<String, String> headers) {
        return createMockResponse(statusCode, headers, new StringWriter());
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

package com.campusverse;

import com.campusverse.dao.DoubtDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Doubt;
import com.campusverse.model.DoubtAnswer;
import com.campusverse.model.Student;
import com.campusverse.service.DoubtService;
import com.campusverse.servlet.DoubtServlet;
import com.campusverse.servlet.DoubtAnswerServlet;
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
public class Phase3DoubtDropTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_phase3_test";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static DoubtService doubtService;
    private static StudentDAO studentDAO;
    private static DoubtDAO doubtDAO;

    @BeforeAll
    static void setUp() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        doubtDAO = new DoubtDAO();
        doubtService = new DoubtService(doubtDAO);
    }

    @Test
    @Order(1)
    void testStep1_StudentAPostsJavaDoubtAndItAppears() throws SQLException {
        Student studentA = studentDAO.findOrCreateByNickname("AliceDoubtA");

        Doubt doubt = doubtService.postDoubt(
                studentA.getId(),
                "Java",
                "How to configure connection pool in Servlet?",
                "Trying to handle multiple simultaneous requests without crashing H2."
        );

        assertNotNull(doubt.getId());
        assertEquals("OPEN", doubt.getStatus());
        assertEquals("Java", doubt.getSubject());
        assertEquals("How to configure connection pool in Servlet?", doubt.getTitle());

        // Verify it appears in subject queries
        List<Doubt> javaDoubts = doubtService.getDoubts("Java");
        assertTrue(javaDoubts.stream().anyMatch(d -> d.getId().equals(doubt.getId())));
        Doubt loaded = javaDoubts.stream().filter(d -> d.getId().equals(doubt.getId())).findFirst().orElseThrow();
        assertEquals("AliceDoubtA", loaded.getAuthorNickname());
    }

    @Test
    @Order(2)
    void testStep2_StudentBAnswersAndAnswerAppears() throws SQLException {
        Student studentA = studentDAO.findByNickname("AliceDoubtA");
        Student studentB = studentDAO.findOrCreateByNickname("BobDoubtB");

        List<Doubt> doubts = doubtService.getDoubts("Java");
        Doubt doubt = doubts.get(0);

        DoubtAnswer answer = doubtService.postAnswer(
                doubt.getId(),
                studentB.getId(),
                "Use a DataSource or manage connections with try-with-resources in DAO layer."
        );
        assertNotNull(answer.getId());

        Doubt reloaded = doubtService.getDoubtById(doubt.getId());
        assertEquals(1, reloaded.getAnswers().size());
        assertEquals("BobDoubtB", reloaded.getAnswers().get(0).getAuthorNickname());
        assertTrue(reloaded.getAnswers().get(0).getAnswerText().contains("DataSource"));
    }

    @Test
    @Order(3)
    void testStep3_StudentBAttemptsToResolveAQuestionServerRejects() throws SQLException {
        Student studentB = studentDAO.findByNickname("BobDoubtB");
        List<Doubt> doubts = doubtService.getDoubts("Java");
        Doubt doubt = doubts.get(0);

        // Security check: non-author resolve MUST throw SecurityException
        assertThrows(SecurityException.class, () -> {
            doubtService.resolveDoubt(doubt.getId(), studentB.getId());
        });

        // Verify status remains OPEN
        Doubt checkDoubt = doubtService.getDoubtById(doubt.getId());
        assertEquals("OPEN", checkDoubt.getStatus());
    }

    @Test
    @Order(4)
    void testStep4_StudentAResolvesOwnDoubtStatusChangesAndPersists() throws SQLException {
        Student studentA = studentDAO.findByNickname("AliceDoubtA");
        List<Doubt> doubts = doubtService.getDoubts("Java");
        Doubt doubt = doubts.get(0);

        // Author resolves
        boolean resolved = doubtService.resolveDoubt(doubt.getId(), studentA.getId());
        assertTrue(resolved);

        Doubt afterResolve = doubtService.getDoubtById(doubt.getId());
        assertEquals("RESOLVED", afterResolve.getStatus());
        assertTrue(afterResolve.isResolved());

        // Checkpoint to disk and confirm persistence
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT");
        }

        try (Connection newConn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = newConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT status FROM doubts WHERE id = " + doubt.getId())) {
            assertTrue(rs.next());
            assertEquals("RESOLVED", rs.getString("status"));
        }
    }

    @Test
    @Order(5)
    void testStep5_RejectBlankAndInvalidContent() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("ValidationStudent");

        // Reject invalid subject
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "InvalidSubject", "Title", "Desc"));

        // Reject blank title or description
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "Java", "", "Valid Description"));
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "Java", "   ", "Valid Description"));
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "Java", "Valid Title", ""));
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "Java", "Valid Title", "   "));

        // Title length limit (> 200 chars)
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postDoubt(student.getId(), "Java", "t".repeat(205), "Desc"));

        // Reject blank answer
        Doubt doubt = doubtService.getDoubts("Java").get(0);
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postAnswer(doubt.getId(), student.getId(), ""));
        assertThrows(IllegalArgumentException.class, () ->
                doubtService.postAnswer(doubt.getId(), student.getId(), "   "));
    }

    @Test
    @Order(6)
    void testStep6_ConfirmSqlInjectionParameterizedAndScriptsNeverExecute() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("SecStudent");

        // SQL injection payload in title and description
        String injectionPayload = "'; DROP TABLE students; --";
        Doubt injectedDoubt = doubtService.postDoubt(student.getId(), "DBMS", injectionPayload, "Test desc with " + injectionPayload);
        assertNotNull(injectedDoubt.getId());

        // Confirm students table is completely intact!
        Student check = studentDAO.findById(student.getId());
        assertNotNull(check, "Table 'students' must remain intact after injection payload attempt");

        // XSS payload in title, description, and answer
        String scriptPayload = "<script>alert('pwned')</script><b>BoldHelp</b>";
        Doubt xssDoubt = doubtService.postDoubt(student.getId(), "DSA", "XSS Check", scriptPayload);
        assertFalse(xssDoubt.getDescription().contains("<script>"), "Raw script tag must not exist");
        assertTrue(xssDoubt.getDescription().contains("&lt;script&gt;"), "Script tag must be safely escaped");

        DoubtAnswer xssAnswer = doubtService.postAnswer(xssDoubt.getId(), student.getId(), scriptPayload);
        assertFalse(xssAnswer.getAnswerText().contains("<script>"));
        assertTrue(xssAnswer.getAnswerText().contains("&lt;script&gt;"));
    }

    @Test
    @Order(7)
    void testSubjectFilteringSeparation() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("FilterStudent");

        doubtService.postDoubt(student.getId(), "DSA", "Binary Search Edge Cases", "Handling mid overflow");
        doubtService.postDoubt(student.getId(), "DBMS", "B-Tree vs Hash Index", "Why B-Tree for ranges?");

        List<Doubt> dsaOnly = doubtService.getDoubts("DSA");
        List<Doubt> dbmsOnly = doubtService.getDoubts("DBMS");

        assertTrue(dsaOnly.stream().allMatch(d -> "DSA".equals(d.getSubject())));
        assertTrue(dbmsOnly.stream().allMatch(d -> "DBMS".equals(d.getSubject())));
        assertTrue(dsaOnly.stream().anyMatch(d -> d.getTitle().contains("Binary Search")));
        assertFalse(dsaOnly.stream().anyMatch(d -> d.getTitle().contains("B-Tree")));
    }

    @Test
    @Order(8)
    void testDoubtServletResolveEnforcesSessionAuthorization() throws Exception {
        Student author = studentDAO.findOrCreateByNickname("ServletAuthor");
        Student imposter = studentDAO.findOrCreateByNickname("ServletImposter");

        Doubt doubt = doubtService.postDoubt(author.getId(), "General", "General Subject Question", "Details");

        DoubtServlet servlet = new DoubtServlet();

        // 1. Imposter attempts to resolve via POST /doubts action=resolve
        HttpSession imposterSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName()) && "currentStudent".equals(args[0])) return imposter;
                    return null;
                }
        );

        HttpServletRequest reqImposter = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getSession".equals(name)) return imposterSession;
                    if ("getParameter".equals(name) && args != null) {
                        if ("action".equals(args[0])) return "resolve";
                        if ("doubtId".equals(args[0])) return String.valueOf(doubt.getId());
                    }
                    if ("getContextPath".equals(name)) return "/campusverse-lite";
                    return null;
                }
        );

        final int[] errorStatus = new int[1];
        HttpServletResponse respImposter = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendError".equals(method.getName()) && args != null) {
                        errorStatus[0] = (int) args[0];
                    }
                    return null;
                }
        );

        servlet.service(reqImposter, respImposter);

        // Server MUST return 403 Forbidden!
        assertEquals(HttpServletResponse.SC_FORBIDDEN, errorStatus[0], "Non-author resolve must be rejected with 403 Forbidden");
    }
}

package com.campusverse;

import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.MatchResult;
import com.campusverse.model.Student;
import com.campusverse.service.VibeMatchService;
import com.campusverse.servlet.StudentServlet;
import com.campusverse.util.DatabaseManager;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4VibeMatchTest {

    private static final String TEST_DB_PATH = "./target/testdb/campusverse_phase4_test";
    private static final String TEST_DB_URL = "jdbc:h2:" + TEST_DB_PATH + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    private static VibeMatchService vibeMatchService;
    private static StudentDAO studentDAO;

    @BeforeAll
    static void setUp() {
        System.setProperty("campusverse.db.url", TEST_DB_URL);
        DatabaseManager.initSchema();
        studentDAO = new StudentDAO();
        vibeMatchService = new VibeMatchService(studentDAO);
    }

    @Test
    @Order(1)
    void testJaccardSimilarityAlgorithmWorkedExamples() {
        // 1. Worked example from handbook:
        // A = {Java, DSA, Hackathons}, B = {Java, React, Hackathons} -> 2/4 = 50%
        List<String> listA = List.of("Java", "DSA", "Hackathons");
        List<String> listB = List.of("Java", "React", "Hackathons");
        int score = VibeMatchService.calculateJaccardScore(listA, listB);
        assertEquals(50, score, "Jaccard similarity should be exactly 50%");

        // 2. 100% identical (case-insensitive)
        List<String> identical1 = List.of("Java", "Python", "AI");
        List<String> identical2 = List.of("java", "PYTHON", "ai");
        assertEquals(100, VibeMatchService.calculateJaccardScore(identical1, identical2));

        // 3. 0% disjoint
        List<String> disjoint1 = List.of("Java", "Python");
        List<String> disjoint2 = List.of("Gaming", "Photography");
        assertEquals(0, VibeMatchService.calculateJaccardScore(disjoint1, disjoint2));

        // 4. 0% empty
        assertEquals(0, VibeMatchService.calculateJaccardScore(List.of(), List.of("Java")));
        assertEquals(0, VibeMatchService.calculateJaccardScore(List.of("Java"), List.of()));
        assertEquals(0, VibeMatchService.calculateJaccardScore(null, List.of("Java")));
        assertEquals(0, VibeMatchService.calculateJaccardScore(List.of(), List.of()));
    }

    @Test
    @Order(2)
    void testDuplicateInterestsDoNotIncreaseScore() {
        // Duplicates and casing normalization do not inflate score
        List<String> withDupsA = List.of("Java", "java", "JAVA", "DSA");
        List<String> withDupsB = List.of("Java", "React", "Hackathons");
        // Normalized: A = {java, dsa} (size 2), B = {java, react, hackathons} (size 3)
        // Intersection = {java} (1), Union = {java, dsa, react, hackathons} (4) -> 1/4 = 25%
        assertEquals(25, VibeMatchService.calculateJaccardScore(withDupsA, withDupsB));
    }

    @Test
    @Order(3)
    void testOwnProfileDoesNotAppearInRecommendations() throws SQLException {
        Student current = studentDAO.findOrCreateByNickname("VibeMainUser");
        studentDAO.findOrCreateByNickname("PeerUserA");
        studentDAO.findOrCreateByNickname("PeerUserB");

        List<MatchResult> matches = vibeMatchService.findMatches(current.getId());
        assertNotNull(matches);

        // Verify own profile does NOT appear in matches
        boolean containsSelf = matches.stream().anyMatch(m -> m.getPeer().getId().equals(current.getId()));
        assertFalse(containsSelf, "Current student must be strictly excluded from match results");
    }

    @Test
    @Order(4)
    void testRecommendationsRankingIsDescendingAndStable() throws SQLException {
        Student studentMain = studentDAO.findOrCreateByNickname("RankingMain");
        Student highMatch = studentDAO.findOrCreateByNickname("RankingHigh");
        Student midMatch = studentDAO.findOrCreateByNickname("RankingMid");
        Student lowMatch = studentDAO.findOrCreateByNickname("RankingLow");

        List<Interest> allInterests = studentDAO.getAllInterests();
        Interest java = allInterests.stream().filter(i -> "Java".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();
        Interest dsa = allInterests.stream().filter(i -> "DSA".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();
        Interest python = allInterests.stream().filter(i -> "Python".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();
        Interest ai = allInterests.stream().filter(i -> "AI".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();
        Interest gaming = allInterests.stream().filter(i -> "Gaming".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();

        // Main: Java, DSA, Python, AI
        studentDAO.setStudentInterests(studentMain.getId(), List.of(java.getId(), dsa.getId(), python.getId(), ai.getId()));

        // High: Java, DSA, Python (3/4 = 75%)
        studentDAO.setStudentInterests(highMatch.getId(), List.of(java.getId(), dsa.getId(), python.getId()));

        // Mid: Java, DSA (2/4 = 50%)
        studentDAO.setStudentInterests(midMatch.getId(), List.of(java.getId(), dsa.getId()));

        // Low: Gaming (0/5 = 0%)
        studentDAO.setStudentInterests(lowMatch.getId(), List.of(gaming.getId()));

        List<MatchResult> matches = vibeMatchService.findMatches(studentMain.getId());

        // Verify strictly descending order
        for (int i = 0; i < matches.size() - 1; i++) {
            assertTrue(matches.get(i).getMatchPercentage() >= matches.get(i + 1).getMatchPercentage(),
                    "Matches must be sorted descending by match percentage");
        }

        // Verify ordering: High > Mid > Low
        int highIdx = -1, midIdx = -1, lowIdx = -1;
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).getPeer().getId().equals(highMatch.getId())) highIdx = i;
            if (matches.get(i).getPeer().getId().equals(midMatch.getId())) midIdx = i;
            if (matches.get(i).getPeer().getId().equals(lowMatch.getId())) lowIdx = i;
        }

        assertTrue(highIdx < midIdx, "High match (75%) must precede Mid match (50%)");
        assertTrue(midIdx < lowIdx, "Mid match (50%) must precede Low match (0%)");
    }

    @Test
    @Order(5)
    void testRejectAttemptsToModifyAnotherStudentsInterests() throws Exception {
        Student victim = studentDAO.findOrCreateByNickname("VictimStudent");
        Student attacker = studentDAO.findOrCreateByNickname("AttackerStudent");

        List<Interest> allInterests = studentDAO.getAllInterests();
        Interest java = allInterests.stream().filter(i -> "Java".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();
        Interest python = allInterests.stream().filter(i -> "Python".equalsIgnoreCase(i.getName())).findFirst().orElseThrow();

        // Set victim's initial interest to Java
        studentDAO.setStudentInterests(victim.getId(), List.of(java.getId()));

        // Attacker attempts to forge request to change victim's interests
        StudentServlet servlet = new StudentServlet();

        HttpSession attackerSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName()) && "currentStudent".equals(args[0])) return attacker;
                    return null;
                }
        );

        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) return "POST";
                    if ("getSession".equals(name)) return attackerSession;
                    if ("getParameter".equals(name) && args != null) {
                        if ("action".equals(args[0])) return "updateInterests";
                        if ("studentId".equals(args[0])) return String.valueOf(victim.getId()); // Injected victim ID!
                    }
                    if ("getParameterValues".equals(name) && "interestIds".equals(args[0])) {
                        return new String[]{String.valueOf(python.getId())};
                    }
                    if ("getContextPath".equals(name)) return "/campusverse-lite";
                    return null;
                }
        );

        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> null
        );

        servlet.service(req, resp);

        // Verify victim's interests were NOT modified (still has Java only)
        List<Interest> victimInterests = studentDAO.getStudentInterests(victim.getId());
        assertEquals(1, victimInterests.size());
        assertEquals(java.getId(), victimInterests.get(0).getId());

        // Verify attacker's own interests were updated instead
        List<Interest> attackerInterests = studentDAO.getStudentInterests(attacker.getId());
        assertEquals(1, attackerInterests.size());
        assertEquals(python.getId(), attackerInterests.get(0).getId());
    }

    @Test
    @Order(6)
    void testInterestChangesPersistAndDoNotDuplicateRecords() throws SQLException {
        Student student = studentDAO.findOrCreateByNickname("PersistenceVibeUser");
        List<Interest> allInterests = studentDAO.getAllInterests();

        // Submitting duplicate IDs in request
        List<Long> duplicateIds = List.of(
                allInterests.get(0).getId(),
                allInterests.get(0).getId(),
                allInterests.get(1).getId()
        );
        studentDAO.setStudentInterests(student.getId(), duplicateIds);

        // Verify database contains unique records only
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_interests WHERE student_id = " + student.getId())) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "Database must contain exactly 2 unique interest mappings without duplicates");
        }
    }
}

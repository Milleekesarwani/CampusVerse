# CampusVerse Lite

> **Connect. Learn. Collaborate.**  
> A Java-based Campus Chat and Academic Collaboration Platform built for university Project Based Learning in Java (PBLJ).

---

## 1. Project Overview & Problem Statement
### Problem Statement
In higher education campus environments, students frequently struggle to find peer collaborators across shared courses, ask academic doubts without intimidating public forums, or engage in temporary, distraction-free study sprint chats. Existing platforms are either too heavy, full of third-party advertisements, or disconnected from academic subject topics.

### Objectives
- Deliver a lightweight, secure, and fast Java web collaboration platform.
- Provide subject-focused rooms (`Java`, `DSA`, `DBMS`, `General`).
- Enable peer-to-peer academic Q&A with author-controlled resolution (`DoubtDrop`).
- Suggest study partners via an explainable mathematical algorithm (`VibeMatch` Jaccard similarity index).
- Facilitate ephemeral sprint discussions with auto-expiring temporary rooms (`GhostRooms`).
- Avoid external framework bloat by strictly relying on pure Jakarta EE Servlets, JDBC, and H2.

---

## 2. Mandatory Stack & Architectural Compliance
- **Platform:** Java 21+ / Jakarta EE 10 Servlet 6.0 (`jakarta.servlet.*`)
- **Database:** H2 Database Engine in persistent file-backed mode (`./data/campusverse_db`)
- **Database Access:** Pure JDBC using `PreparedStatement` exclusively (zero ORM/Hibernate bloat)
- **Application Server:** Apache Tomcat 10.1+
- **Build & Tests:** Apache Maven 3.9+ & JUnit 5 Jupiter
- **Security:** Server-side `HttpSession` verification, HTML escaping against XSS, SQL parameterization, CSRF tokens, and UTF-8 encoding filters.

### Package Architecture
```
com.campusverse
├── model/        # Domain entities (Student, Interest, Room, Message, Doubt, DoubtAnswer, MatchResult)
├── dao/          # Pure JDBC Data Access Objects with PreparedStatements (StudentDAO, RoomDAO, MessageDAO, DoubtDAO)
├── service/      # Business logic & algorithms (ChatService, DoubtService, VibeMatchService, GhostRoomService)
├── servlet/      # Jakarta HTTP Servlets (HomeServlet, StudentServlet, DashboardServlet, ChatServlet, MessageApiServlet, DoubtServlet, DoubtAnswerServlet, VibeMatchServlet, GhostRoomServlet)
└── util/         # DatabaseManager, SecurityUtils, SecurityFilter, AppContextListener
```

---

## 3. Database Summary & Schema
The H2 database initializes idempotently upon application boot:
- **`students`**: `id` (BIGINT PK), `nickname` (VARCHAR(50) UNIQUE), `created_at` (TIMESTAMP)
- **`interests`**: `id` (BIGINT PK), `name` (VARCHAR(100) UNIQUE)
- **`student_interests`**: `student_id` (FK to students), `interest_id` (FK to interests), Composite PK `(student_id, interest_id)`
- **`rooms`**: `id` (BIGINT PK), `name` (VARCHAR(100)), `type` (`STUDY` / `GHOST`), `created_at` (TIMESTAMP), `expires_at` (TIMESTAMP nullable), `status` (`ACTIVE` / `CLOSED`)
- **`messages`**: `id` (BIGINT PK), `room_id` (FK to rooms), `student_id` (FK to students), `content` (VARCHAR(2000)), `created_at` (TIMESTAMP)
- **`doubts`**: `id` (BIGINT PK), `student_id` (FK to students), `subject` (VARCHAR(50)), `title` (VARCHAR(200)), `description` (CLOB), `status` (`OPEN` / `RESOLVED`), `created_at` (TIMESTAMP)
- **`doubt_answers`**: `id` (BIGINT PK), `doubt_id` (FK to doubts), `student_id` (FK to students), `answer_text` (CLOB), `created_at` (TIMESTAMP)

---

## 4. Implemented Features
1. **Study Chat:**
   - 4 seed rooms: `Java`, `DSA`, `DBMS`, `General`.
   - Cursor-based message polling (`/api/messages?roomId=...&afterId=...`) avoiding duplicate messages.
   - Real-time client updates every 3 seconds via vanilla `fetch()`.
2. **DoubtDrop:**
   - Post subject-categorized academic questions.
   - Peer answers visible chronologically.
   - Server-enforced authorization: **Only the question author** can mark a doubt `RESOLVED`.
3. **VibeMatch:**
   - Jaccard similarity index: $\text{Score} = 100 \times \frac{|A \cap B|}{|A \cup B|}$.
   - Evaluated using Java `HashSet`, normalized and deduplicated.
   - Ranked descending with deterministic tie-breaking.
4. **GhostRooms:**
   - Ephemeral group chats configured for 5m, 1h, 6h, or 24h.
   - Server-side UTC expiration checks on every message submission.
   - Automatically switches to `CLOSED` after expiration while preserving historical messages.

---

## 5. Limitations
- **Prototype Identity:** Uses nickname-based session identity designed for university demo prototypes, not hardened production SSO / OAuth.
- **Polling:** MVP utilizes HTTP cursor polling (every 3 seconds) instead of WebSockets.

---

## 6. How to Build & Run
### Build the WAR
```powershell
mvn clean verify
```
### Run the Automated Test Suite (23 Tests)
```powershell
mvn test
```
### Start Apache Tomcat 10.1
Deploy `target/campusverse-lite.war` to your Tomcat `webapps/` folder and execute:
```powershell
C:\Users\mille\.tomcat\apache-tomcat-10.1.61\bin\startup.bat
```
Open your browser at:
```
http://localhost:8080/campusverse-lite/home
```

---

## 7. 5-Minute Evaluation Demo Plan
1. **Minute 1 - Profile & Identity:** Open two browser windows (Regular & Incognito). Enter `DevAlex` in Window 1 and `CodeSam` in Window 2. Select interests on the Dashboard and click *Save Interests*.
2. **Minute 2 - Study Chat Polling:** In both windows, join the `Java` study room. Send a message from `DevAlex`. Within ~3 seconds, observe the message appear in `CodeSam`'s window without a full page reload. Send a reply.
3. **Minute 3 - DoubtDrop Resolution Security:** In Window 1 (`DevAlex`), post a Doubt in `DSA`. Switch to Window 2 (`CodeSam`) and submit an answer. Verify that `CodeSam` has no permission to resolve the doubt. In Window 1 (`DevAlex`), click *Mark as RESOLVED* and verify the badge turns green.
4. **Minute 4 - VibeMatch Verification:** Go to VibeMatch. Verify that `DevAlex` sees `CodeSam` with the exact computed Jaccard percentage based on their shared interests.
5. **Minute 5 - GhostRooms Expiration:** Create a 5-minute GhostRoom. Send a message while active. Wait or verify expired room status transition to `CLOSED` and verify new message submissions are rejected.

---

## 8. 10 Java Viva Questions & Answers
1. **Q: Why Jakarta Servlet 6.0 instead of javax.servlet?**  
   *A:* Following the transition of Java EE to the Eclipse Foundation (Jakarta EE 9+), all servlet packages moved from `javax.servlet` to `jakarta.servlet`. Tomcat 10.1 implements the Jakarta EE 10 Servlet 6.0 specification.
2. **Q: How is SQL Injection prevented throughout the application?**  
   *A:* Every database interaction uses JDBC `PreparedStatement` with parameterized placeholders (`?`). No string concatenation is used in queries.
3. **Q: How does VibeMatch calculate similarity between two students?**  
   *A:* It uses the Jaccard similarity index: $\frac{|A \cap B|}{|A \cup B|} \times 100$. Interests are deduplicated and normalized into Java `HashSet`s before calculating intersection and union.
4. **Q: How is chat polling made efficient without delivering duplicate messages?**  
   *A:* The client maintains `lastMessageId` and calls `GET /api/messages?roomId=...&afterId=...`. The backend query `WHERE room_id = ? AND id > ?` returns only newer messages.
5. **Q: How does the server enforce GhostRoom expiration?**  
   *A:* Expiration is validated on the backend inside `ChatService.postMessage` on *every* submission by comparing `Instant.now()` against `room.getExpiresAt()`. A manipulated client cannot bypass expiration.
6. **Q: Why use `HttpSession` instead of trusting hidden form fields for student identity?**  
   *A:* Hidden form fields can be spoofed using browser developer tools. `HttpSession` maintains student credentials securely in server memory linked to an HTTP-only session cookie.
7. **Q: How is Cross-Site Scripting (XSS) mitigated?**  
   *A:* All user-entered text is sanitized through `SecurityUtils.escapeHtml()` before database storage and rendered safely using `textContent` in the DOM.
8. **Q: Why is H2 configured in file-backed mode rather than in-memory?**  
   *A:* `jdbc:h2:./data/campusverse_db` ensures messages, doubts, students, and rooms persist across server restarts, whereas in-memory databases lose all state upon process termination.
9. **Q: What is the POST-Redirect-GET (PRG) pattern and where is it used?**  
   *A:* PRG ensures that after processing a POST request (e.g., student login, creating a doubt), the server responds with a 302 redirect. This prevents duplicate submissions if the user refreshes their browser.
10. **Q: What is the purpose of `AppContextListener`?**  
    *A:* It implements `ServletContextListener` to hook into the web application lifecycle, initializing the database tables and seeding default study rooms when Tomcat starts.

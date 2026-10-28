<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.model.Room" %>
<%@ page import="com.campusverse.model.Interest" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.stream.Collectors" %>
<%
    Student currentStudent = (Student) request.getAttribute("student");
    List<Room> studyRooms = (List<Room>) request.getAttribute("studyRooms");
    List<Interest> allInterests = (List<Interest>) request.getAttribute("allInterests");
    List<Interest> studentInterests = (List<Interest>) request.getAttribute("studentInterests");
    Set<Long> myInterestIds = studentInterests != null
            ? studentInterests.stream().map(Interest::getId).collect(Collectors.toSet())
            : Set.of();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
    <header class="app-header">
        <div class="container navbar">
            <div class="brand">
                <a href="${pageContext.request.contextPath}/dashboard" class="logo-link">CampusVerse Lite</a>
            </div>
            <nav class="nav-links">
                <a href="${pageContext.request.contextPath}/dashboard" class="active">Dashboard</a>
                <a href="${pageContext.request.contextPath}/doubts">DoubtDrop</a>
                <a href="${pageContext.request.contextPath}/vibematch">VibeMatch</a>
                <a href="${pageContext.request.contextPath}/ghostrooms">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= currentStudent != null ? currentStudent.getNickname() : "Student" %></strong></span>
                <form action="${pageContext.request.contextPath}/student" method="post" style="display:inline;">
                    <input type="hidden" name="action" value="logout">
                    <button type="submit" class="btn btn-sm btn-outline">Logout</button>
                </form>
            </div>
        </div>
    </header>

    <main class="container">
        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= request.getParameter("error") %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= request.getParameter("msg") %></div>
        <% } %>

        <div class="dashboard-grid">
            <!-- Study Rooms Section -->
            <section class="rooms-section">
                <h2>📚 Subject Study Rooms</h2>
                <p>Select a room to begin peer messaging and discussion:</p>
                <div class="room-cards">
                    <% if (studyRooms != null) {
                        for (Room r : studyRooms) { %>
                            <div class="room-card">
                                <div>
                                    <h3><%= r.getName() %></h3>
                                    <span class="badge badge-open">Active Study Room</span>
                                </div>
                                <a href="${pageContext.request.contextPath}/chat?roomId=<%= r.getId() %>" class="btn btn-primary">Join Room</a>
                            </div>
                    <%  }
                       } %>
                </div>
            </section>

            <!-- Interests Profile Section -->
            <section class="interests-section">
                <h2>✨ Your Tech Interests</h2>
                <p>Select your areas of focus to power your VibeMatch recommendations:</p>
                <form action="${pageContext.request.contextPath}/student" method="post">
                    <input type="hidden" name="action" value="updateInterests">
                    <div class="interest-checkboxes">
                        <% if (allInterests != null) {
                            for (Interest i : allInterests) {
                                boolean checked = myInterestIds.contains(i.getId());
                        %>
                            <label class="interest-label">
                                <input type="checkbox" name="interestIds" value="<%= i.getId() %>" <%= checked ? "checked" : "" %>>
                                <span><%= i.getName() %></span>
                            </label>
                        <%  }
                           } %>
                    </div>
                    <button type="submit" class="btn btn-secondary" style="margin-top: 1rem;">Save Interests</button>
                </form>
            </section>
        </div>
    </main>

    <footer class="app-footer">
        <div class="container">
            <p>&copy; 2026 CampusVerse Lite - University Mini-Project</p>
        </div>
    </footer>
</body>
</html>


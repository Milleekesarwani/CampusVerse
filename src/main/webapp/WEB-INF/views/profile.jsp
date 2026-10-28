<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.model.Interest" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    Student currentStudent = (Student) session.getAttribute("currentStudent");
    List<Interest> allInterests = (List<Interest>) request.getAttribute("allInterests");
    List<Interest> studentInterests = (List<Interest>) request.getAttribute("studentInterests");
    Set<Long> myInterestIds = studentInterests != null
            ? studentInterests.stream().map(Interest::getId).collect(Collectors.toSet())
            : Set.of();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String studentName = currentStudent != null ? SecurityUtils.escapeHtml(currentStudent.getNickname()) : "Student";
    String csrfToken = (String) request.getAttribute("csrfToken");
    if (csrfToken == null && session != null) {
        csrfToken = (String) session.getAttribute("csrfToken");
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
    <title>Student Profile - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
    <header class="app-header">
        <div class="container navbar">
            <div class="brand">
                <a href="${pageContext.request.contextPath}/home" class="logo-link">CampusVerse Lite</a>
            </div>
            <nav class="nav-links">
                <a href="${pageContext.request.contextPath}/dashboard">Dashboard</a>
                <a href="${pageContext.request.contextPath}/doubts">DoubtDrop</a>
                <a href="${pageContext.request.contextPath}/vibematch">VibeMatch</a>
                <a href="${pageContext.request.contextPath}/ghostrooms">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= studentName %></strong></span>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-sm btn-outline">Dashboard</a>
            </div>
        </div>
    </header>

    <main class="container">
        <h2>👤 Student Profile</h2>
        <p style="color: var(--text-muted); margin-bottom: 1.5rem;">Manage your campus identity and academic interest tags.</p>

        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= SecurityUtils.escapeHtml(request.getParameter("error")) %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= SecurityUtils.escapeHtml(request.getParameter("msg")) %></div>
        <% } %>

        <div class="dashboard-grid">
            <section class="rooms-section">
                <h3>Profile Details</h3>
                <div style="margin-top: 1rem; line-height: 2;">
                    <div><strong>Student Nickname:</strong> <%= studentName %></div>
                    <div><strong>Student ID:</strong> #<%= currentStudent != null ? currentStudent.getId() : "" %></div>
                    <div><strong>Registered Since:</strong> <%= currentStudent != null && currentStudent.getCreatedAt() != null ? sdf.format(currentStudent.getCreatedAt()) : "Recently" %></div>
                </div>

                <div style="margin-top: 2rem;">
                    <form action="${pageContext.request.contextPath}/student" method="post">
                        <input type="hidden" name="action" value="logout">
                        <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
                        <button type="submit" class="btn btn-primary" style="background:#dc2626;">Log Out of Session</button>
                    </form>
                </div>
            </section>

            <section class="interests-section">
                <h3>Update Technical Interests</h3>
                <p style="color: var(--text-muted); font-size: 0.9rem; margin-bottom: 1rem;">Changes take effect immediately across VibeMatch.</p>
                <form action="${pageContext.request.contextPath}/student" method="post">
                    <input type="hidden" name="action" value="updateInterests">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
                    <div class="interest-checkboxes">
                        <% if (allInterests != null) {
                            for (Interest i : allInterests) {
                                boolean checked = myInterestIds.contains(i.getId());
                        %>
                            <label class="interest-label">
                                <input type="checkbox" name="interestIds" value="<%= i.getId() %>" <%= checked ? "checked" : "" %>>
                                <span><%= SecurityUtils.escapeHtml(i.getName()) %></span>
                            </label>
                        <%  }
                           } %>
                    </div>
                    <button type="submit" class="btn btn-secondary" style="margin-top: 1.25rem;">Save Interests</button>
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

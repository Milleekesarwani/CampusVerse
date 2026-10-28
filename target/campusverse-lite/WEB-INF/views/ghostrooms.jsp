<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Room" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="java.util.List" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.time.Instant" %>
<%
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    List<Room> ghostRooms = (List<Room>) request.getAttribute("ghostRooms");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    long nowMillis = System.currentTimeMillis();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>GhostRooms - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .ghost-card {
            background: white;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1.25rem;
            margin-bottom: 1rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .countdown {
            font-size: 0.85rem;
            font-weight: 600;
            color: #d97706;
        }
    </style>
</head>
<body>
    <header class="app-header">
        <div class="container navbar">
            <div class="brand">
                <a href="${pageContext.request.contextPath}/dashboard" class="logo-link">CampusVerse Lite</a>
            </div>
            <nav class="nav-links">
                <a href="${pageContext.request.contextPath}/dashboard">Dashboard</a>
                <a href="${pageContext.request.contextPath}/doubts">DoubtDrop</a>
                <a href="${pageContext.request.contextPath}/vibematch">VibeMatch</a>
                <a href="${pageContext.request.contextPath}/ghostrooms" class="active">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= currentStudent != null ? currentStudent.getNickname() : "Student" %></strong></span>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-sm btn-outline">Dashboard</a>
            </div>
        </div>
    </header>

    <main class="container">
        <h2>👻 GhostRooms (Ephemeral Study Chats)</h2>
        <p style="color: var(--text-muted); margin-bottom: 1.5rem;">
            Temporary collaboration rooms that automatically lock and reject new messages after their timer expires.
        </p>

        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= request.getParameter("error") %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= request.getParameter("msg") %></div>
        <% } %>

        <div class="dashboard-grid">
            <!-- GhostRooms list -->
            <div>
                <h3>Active &amp; Archived GhostRooms</h3>
                <div style="margin-top: 1rem;">
                    <% if (ghostRooms != null && !ghostRooms.isEmpty()) {
                        for (Room r : ghostRooms) {
                            boolean expired = r.isExpired();
                            long remainingSeconds = r.getExpiresAt() != null ? (r.getExpiresAt().getTime() - nowMillis) / 1000 : 0;
                            long remainingMins = Math.max(0, remainingSeconds / 60);
                    %>
                        <div class="ghost-card">
                            <div>
                                <h4 style="font-size: 1.1rem; margin-bottom: 0.25rem;"><%= r.getName() %></h4>
                                <div style="font-size: 0.85rem; color: var(--text-muted);">
                                    Created: <%= r.getCreatedAt() != null ? sdf.format(r.getCreatedAt()) : "" %> &bull;
                                    Expires: <%= r.getExpiresAt() != null ? sdf.format(r.getExpiresAt()) : "N/A" %>
                                </div>
                                <% if (!expired && remainingSeconds > 0) { %>
                                    <div class="countdown">⏳ Remaining: ~<%= remainingMins %> minute(s)</div>
                                <% } %>
                            </div>

                            <div style="display:flex; align-items:center; gap: 1rem;">
                                <span class="badge <%= expired ? "badge-closed" : "badge-open" %>">
                                    <%= expired ? "CLOSED / Expired" : "ACTIVE" %>
                                </span>
                                <a href="${pageContext.request.contextPath}/chat?roomId=<%= r.getId() %>" class="btn <%= expired ? "btn-outline" : "btn-primary" %>" style="<%= expired ? "color:#475569; border-color:#cbd5e1;" : "" %>">
                                    <%= expired ? "View Log" : "Enter Room" %>
                                </a>
                            </div>
                        </div>
                    <%  }
                       } else { %>
                        <p style="color: var(--text-muted); text-align: center; padding: 2rem; background: white; border-radius: 8px;">No GhostRooms active yet. Create one on the right!</p>
                    <% } %>
                </div>
            </div>

            <!-- Create GhostRoom Form -->
            <div>
                <section class="rooms-section">
                    <h3>⚡ Spawn GhostRoom</h3>
                    <p style="color: var(--text-muted); font-size: 0.9rem; margin-bottom: 1rem;">Choose a duration. The room will accept messages until expiry.</p>
                    <form action="${pageContext.request.contextPath}/ghostrooms" method="post">
                        <div class="form-group">
                            <label for="roomName">Room Topic / Name:</label>
                            <input type="text" id="roomName" name="name" required maxlength="100" placeholder="e.g. Java Exam Cram Session">
                        </div>
                        <div class="form-group">
                            <label for="duration">Room Duration:</label>
                            <select id="duration" name="duration" required>
                                <option value="5 minutes">5 minutes (Quick doubt burst)</option>
                                <option value="1 hour">1 hour (Study session)</option>
                                <option value="6 hours">6 hours (Evening sprint)</option>
                                <option value="24 hours">24 hours (Day-long hack/prep)</option>
                            </select>
                        </div>
                        <button type="submit" class="btn btn-secondary" style="width: 100%;">Create GhostRoom</button>
                    </form>
                </section>
            </div>
        </div>
    </main>

    <footer class="app-footer">
        <div class="container">
            <p>&copy; 2026 CampusVerse Lite - University Mini-Project</p>
        </div>
    </footer>
</body>
</html>


<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.campusverse.model.MatchResult" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.model.Room" %>
<%@ page import="com.campusverse.model.Interest" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%@ page import="java.util.List" %>
<%
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    List<MatchResult> matches = (List<MatchResult>) request.getAttribute("matches");
    List<Room> studyRooms = (List<Room>) request.getAttribute("studyRooms");
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
    <title>VibeMatch - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .match-card {
            background: white;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1.25rem;
            margin-bottom: 1rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }
        .match-score {
            font-size: 1.75rem;
            font-weight: 800;
            color: var(--primary-color);
            text-align: center;
            min-width: 90px;
        }
        .match-score span {
            display: block;
            font-size: 0.75rem;
            font-weight: 600;
            color: var(--text-muted);
            text-transform: uppercase;
        }
        .tag {
            display: inline-block;
            background: #e0f2fe;
            color: #0369a1;
            padding: 0.2rem 0.5rem;
            border-radius: 4px;
            font-size: 0.8rem;
            margin-right: 0.3rem;
            margin-top: 0.3rem;
        }
    </style>
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
                <a href="${pageContext.request.contextPath}/vibematch" class="active">VibeMatch</a>
                <a href="${pageContext.request.contextPath}/ghostrooms">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= studentName %></strong></span>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-sm btn-outline">Dashboard</a>
            </div>
        </div>
    </header>

    <main class="container">
        <h2>🤝 VibeMatch Peer Recommendations</h2>
        <p style="color: var(--text-muted); margin-bottom: 1.5rem;">
            Connecting you with study partners using mathematical Jaccard similarity index based on shared interests.
        </p>

        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= SecurityUtils.escapeHtml(request.getParameter("error")) %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= SecurityUtils.escapeHtml(request.getParameter("msg")) %></div>
        <% } %>

        <!-- Current Student Interests Summary -->
        <section class="rooms-section" style="margin-bottom: 1.5rem;">
            <h4>Your Selected Interests (<%= currentStudent != null && currentStudent.getInterests() != null ? currentStudent.getInterests().size() : 0 %>)</h4>
            <div style="margin-top: 0.5rem;">
                <% if (currentStudent != null && currentStudent.getInterests() != null && !currentStudent.getInterests().isEmpty()) {
                    for (Interest i : currentStudent.getInterests()) { %>
                        <span class="tag"><%= SecurityUtils.escapeHtml(i.getName()) %></span>
                <%  }
                   } else { %>
                    <p style="color: var(--text-muted);">You haven't selected any interests yet. <a href="${pageContext.request.contextPath}/dashboard">Update on your Dashboard</a> to get better matches!</p>
                <% } %>
            </div>
        </section>

        <!-- Matched Peers List -->
        <div class="matches-list">
            <% if (matches != null && !matches.isEmpty()) {
                for (MatchResult m : matches) {
                    Student peer = m.getPeer();
            %>
                <div class="match-card">
                    <div style="flex: 1;">
                        <h3 style="color: var(--text-color); margin-bottom: 0.25rem;">
                            👤 <%= SecurityUtils.escapeHtml(peer.getNickname()) %>
                        </h3>
                        <div style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 0.5rem;">
                            Total peer interests: <%= peer.getInterests() != null ? peer.getInterests().size() : 0 %>
                        </div>
                        <div>
                            <strong>Shared Topics:</strong>
                            <% if (m.getSharedInterests() != null && !m.getSharedInterests().isEmpty()) {
                                for (String topic : m.getSharedInterests()) { %>
                                    <span class="tag" style="background:#dcfce7; color:#166534;"><%= SecurityUtils.escapeHtml(topic) %></span>
                            <%  }
                               } else { %>
                                <span style="color: var(--text-muted); font-size: 0.85rem;">None yet</span>
                            <% } %>
                        </div>
                    </div>

                    <div style="display:flex; align-items:center; gap: 1.5rem;">
                        <div class="match-score">
                            <%= m.getMatchPercentage() %>%
                            <span>Jaccard Index</span>
                        </div>
                        <div>
                            <% if (studyRooms != null && !studyRooms.isEmpty()) { %>
                                <a href="${pageContext.request.contextPath}/chat?roomId=<%= studyRooms.get(0).getId() %>" class="btn btn-primary">Join Study Room</a>
                            <% } %>
                        </div>
                    </div>
                </div>
            <%  }
               } else { %>
                <p style="color: var(--text-muted); text-align: center; padding: 2rem; background: white; border-radius: 8px;">No other student profiles found yet. Invite your classmates to join CampusVerse!</p>
            <% } %>
        </div>
    </main>

    <footer class="app-footer">
        <div class="container">
            <p>&copy; 2026 CampusVerse Lite - University Mini-Project</p>
        </div>
    </footer>
</body>
</html>

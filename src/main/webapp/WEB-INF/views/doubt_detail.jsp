<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Doubt" %>
<%@ page import="com.campusverse.model.DoubtAnswer" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    Doubt doubt = (Doubt) request.getAttribute("doubt");
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    boolean isAuthor = doubt != null && currentStudent != null && currentStudent.getId().equals(doubt.getStudentId());
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= doubt != null ? doubt.getTitle() : "Doubt" %> - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .detail-card {
            background: white;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1.5rem;
            margin-bottom: 1.5rem;
        }
        .answer-item {
            background: #f8fafc;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1rem;
            margin-bottom: 1rem;
        }
        .answer-meta {
            font-size: 0.85rem;
            color: var(--text-muted);
            margin-bottom: 0.5rem;
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
                <a href="${pageContext.request.contextPath}/doubts" class="active">DoubtDrop</a>
                <a href="${pageContext.request.contextPath}/vibematch">VibeMatch</a>
                <a href="${pageContext.request.contextPath}/ghostrooms">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= currentStudent != null ? currentStudent.getNickname() : "Student" %></strong></span>
                <a href="${pageContext.request.contextPath}/doubts" class="btn btn-sm btn-outline">&larr; All Doubts</a>
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

        <% if (doubt != null) { %>
            <div class="detail-card">
                <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom: 1rem;">
                    <div>
                        <span class="badge" style="background:#e0e7ff; color:#3730a3; margin-bottom: 0.5rem;"><%= doubt.getSubject() %></span>
                        <h2><%= doubt.getTitle() %></h2>
                        <div style="font-size:0.85rem; color: var(--text-muted); margin-top: 0.25rem;">
                            Posted by <strong><%= doubt.getAuthorNickname() %></strong> on <%= doubt.getCreatedAt() != null ? sdf.format(doubt.getCreatedAt()) : "" %>
                        </div>
                    </div>
                    <div>
                        <span class="badge <%= doubt.isResolved() ? "badge-resolved" : "badge-open" %>">
                            <%= doubt.getStatus() %>
                        </span>
                    </div>
                </div>

                <div style="margin: 1.5rem 0; line-height: 1.6; white-space: pre-wrap; font-size: 1.05rem;"><%= doubt.getDescription() %></div>

                <% if (isAuthor && !doubt.isResolved()) { %>
                    <form action="${pageContext.request.contextPath}/doubts" method="post" style="margin-top: 1rem;">
                        <input type="hidden" name="action" value="resolve">
                        <input type="hidden" name="doubtId" value="<%= doubt.getId() %>">
                        <button type="submit" class="btn btn-primary">✅ Mark as RESOLVED</button>
                    </form>
                <% } %>
            </div>

            <!-- Answers List -->
            <h3>💬 Answers (<%= doubt.getAnswers() != null ? doubt.getAnswers().size() : 0 %>)</h3>
            <div style="margin-top: 1rem;">
                <% if (doubt.getAnswers() != null && !doubt.getAnswers().isEmpty()) {
                    for (DoubtAnswer a : doubt.getAnswers()) { %>
                        <div class="answer-item">
                            <div class="answer-meta">
                                <strong><%= a.getAuthorNickname() %></strong> answered on <%= a.getCreatedAt() != null ? sdf.format(a.getCreatedAt()) : "" %>:
                            </div>
                            <div style="white-space: pre-wrap;"><%= a.getAnswerText() %></div>
                        </div>
                <%  }
                   } else { %>
                    <p style="color: var(--text-muted); margin-bottom: 1.5rem;">No answers yet. Share your knowledge below!</p>
                <% } %>
            </div>

            <!-- Post Answer Form -->
            <section class="rooms-section" style="margin-top: 2rem;">
                <h4>Your Answer</h4>
                <form action="${pageContext.request.contextPath}/doubts/answer" method="post" style="margin-top: 1rem;">
                    <input type="hidden" name="doubtId" value="<%= doubt.getId() %>">
                    <div class="form-group">
                        <textarea name="answerText" rows="4" required placeholder="Type your helpful explanation or solution..."></textarea>
                    </div>
                    <button type="submit" class="btn btn-secondary">Submit Answer</button>
                </form>
            </section>
        <% } %>
    </main>

    <footer class="app-footer">
        <div class="container">
            <p>&copy; 2026 CampusVerse Lite - University Mini-Project</p>
        </div>
    </footer>
</body>
</html>


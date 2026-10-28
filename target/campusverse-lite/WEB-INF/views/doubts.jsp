<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Doubt" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="java.util.List" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    List<Doubt> doubts = (List<Doubt>) request.getAttribute("doubts");
    String currentSubject = (String) request.getAttribute("currentSubject");
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DoubtDrop - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .filter-bar {
            display: flex;
            gap: 0.5rem;
            margin-bottom: 1.5rem;
            flex-wrap: wrap;
        }
        .filter-btn {
            padding: 0.4rem 0.8rem;
            border-radius: 9999px;
            font-size: 0.85rem;
            text-decoration: none;
            background: #e2e8f0;
            color: #334155;
            font-weight: 600;
        }
        .filter-btn.active {
            background: var(--primary-color);
            color: white;
        }
        .doubt-item {
            background: white;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1.25rem;
            margin-bottom: 1rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .doubt-title {
            font-size: 1.15rem;
            font-weight: 700;
            color: var(--primary-dark);
            text-decoration: none;
            display: block;
            margin-bottom: 0.35rem;
        }
        .doubt-meta {
            font-size: 0.85rem;
            color: var(--text-muted);
            display: flex;
            gap: 1rem;
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
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-sm btn-outline">Dashboard</a>
            </div>
        </div>
    </header>

    <main class="container">
        <h2>❓ DoubtDrop Academic Q&amp;A</h2>
        <p style="color: var(--text-muted); margin-bottom: 1.5rem;">Ask academic questions, collaborate on answers, and track resolutions.</p>

        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= request.getParameter("error") %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= request.getParameter("msg") %></div>
        <% } %>

        <div class="dashboard-grid">
            <div>
                <!-- Subject Filter -->
                <div class="filter-bar">
                    <a href="${pageContext.request.contextPath}/doubts?subject=ALL" class="filter-btn <%= "ALL".equals(currentSubject) ? "active" : "" %>">All Subjects</a>
                    <a href="${pageContext.request.contextPath}/doubts?subject=Java" class="filter-btn <%= "Java".equals(currentSubject) ? "active" : "" %>">Java</a>
                    <a href="${pageContext.request.contextPath}/doubts?subject=DSA" class="filter-btn <%= "DSA".equals(currentSubject) ? "active" : "" %>">DSA</a>
                    <a href="${pageContext.request.contextPath}/doubts?subject=DBMS" class="filter-btn <%= "DBMS".equals(currentSubject) ? "active" : "" %>">DBMS</a>
                    <a href="${pageContext.request.contextPath}/doubts?subject=General" class="filter-btn <%= "General".equals(currentSubject) ? "active" : "" %>">General</a>
                </div>

                <!-- Doubts List -->
                <div class="doubts-list">
                    <% if (doubts != null && !doubts.isEmpty()) {
                        for (Doubt d : doubts) { %>
                            <div class="doubt-item">
                                <div>
                                    <a href="${pageContext.request.contextPath}/doubts?id=<%= d.getId() %>" class="doubt-title"><%= d.getTitle() %></a>
                                    <div class="doubt-meta">
                                        <span>Subject: <strong><%= d.getSubject() %></strong></span>
                                        <span>By: <strong><%= d.getAuthorNickname() %></strong></span>
                                        <span><%= d.getCreatedAt() != null ? sdf.format(d.getCreatedAt()) : "" %></span>
                                    </div>
                                </div>
                                <div>
                                    <span class="badge <%= d.isResolved() ? "badge-resolved" : "badge-open" %>">
                                        <%= d.getStatus() %>
                                    </span>
                                </div>
                            </div>
                    <%  }
                       } else { %>
                        <p style="color: var(--text-muted); text-align: center; padding: 2rem; background: white; border-radius: 8px;">No doubts found for this filter. Be the first to ask!</p>
                    <% } %>
                </div>
            </div>

            <!-- Post New Doubt Form -->
            <div>
                <section class="rooms-section">
                    <h3>✍️ Ask a Question</h3>
                    <form action="${pageContext.request.contextPath}/doubts" method="post" style="margin-top: 1rem;">
                        <input type="hidden" name="action" value="create">
                        <div class="form-group">
                            <label for="subject">Subject:</label>
                            <select id="subject" name="subject" required>
                                <option value="Java">Java</option>
                                <option value="DSA">DSA</option>
                                <option value="DBMS">DBMS</option>
                                <option value="General">General</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="title">Title / Summary:</label>
                            <input type="text" id="title" name="title" required maxlength="200" placeholder="e.g. How to use PreparedStatement correctly?">
                        </div>
                        <div class="form-group">
                            <label for="description">Detailed Description:</label>
                            <textarea id="description" name="description" rows="5" required placeholder="Describe what you tried and what you need help with..."></textarea>
                        </div>
                        <button type="submit" class="btn btn-primary" style="width: 100%;">Post Doubt</button>
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


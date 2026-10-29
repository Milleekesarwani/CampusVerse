<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%
    Student currentStudent = (Student) session.getAttribute("currentStudent");
    if (currentStudent == null) {
        currentStudent = (Student) request.getAttribute("currentStudent");
    }
    String studentName = (currentStudent != null && currentStudent.getNickname() != null)
            ? currentStudent.getNickname().replace('_', ' ')
            : "";

    String initials = "ST";
    if (studentName != null && !studentName.trim().isEmpty()) {
        String[] parts = studentName.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        } else {
            initials = studentName.substring(0, Math.min(2, studentName.length())).toUpperCase();
        }
    }

    String csrfToken = (String) request.getAttribute("csrfToken");
    if (csrfToken == null && session != null) {
        csrfToken = (String) session.getAttribute("csrfToken");
    }
%>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="description" content="CampusVerse Lite — your campus, a little closer. Study together, find your people, and get unstuck.">
    <meta name="csrf-token" content="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
    <title>CampusVerse Lite — Your campus, closer.</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>✳</text></svg>">
</head>
<body>
<% if (currentStudent == null) { %>
    <!-- Unauthenticated Nickname Entry Flow -->
    <div style="min-height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 24px;">
        <div style="text-align: center; margin-bottom: 24px;">
            <div class="brand-icon" style="display:inline-block; margin-bottom: 8px;">c<span>✳</span></div>
            <div style="font-family:Manrope,sans-serif; font-size:26px; font-weight:800; letter-spacing:-1px;">campusverse <span style="font-size:12px; font-weight:600; color:var(--purple); letter-spacing:3px;">LITE</span></div>
            <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Chandigarh University · Student Collaboration Platform</div>
        </div>

        <div class="panel" style="max-width: 440px; width: 100%; padding: 32px; box-shadow: 0 12px 40px #2419340f; border-radius: 16px;">
            <% if (request.getParameter("error") != null) { %>
                <div style="background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; padding: 12px 14px; border-radius: 8px; font-size: 12px; margin-bottom: 18px;">
                    <%= SecurityUtils.escapeHtml(request.getParameter("error")) %>
                </div>
            <% } %>
            <% if (request.getParameter("msg") != null) { %>
                <div style="background: #edf7e7; color: #3f6212; border: 1px solid #d9f0c7; padding: 12px 14px; border-radius: 8px; font-size: 12px; margin-bottom: 18px;">
                    <%= SecurityUtils.escapeHtml(request.getParameter("msg")) %>
                </div>
            <% } %>

            <h2 style="font-family:Manrope,sans-serif; font-size: 20px; font-weight: 800; margin: 0 0 8px; letter-spacing: -0.5px;">Enter your campus corner</h2>
            <p style="color: var(--muted); font-size: 12px; line-height: 1.6; margin: 0 0 20px;">Pick a student nickname to start collaborating. No password required.</p>

            <form action="${pageContext.request.contextPath}/student" method="post" class="form-stack">
                <input type="hidden" name="action" value="login">
                <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
                <label style="font-size: 10px; font-weight: 700; color: #5a5563; letter-spacing: 1px;">STUDENT NICKNAME
                    <input type="text" name="nickname" placeholder="e.g. AlexDev, Priya_J, CodeNinja"
                           required maxlength="50" autocomplete="off" autofocus style="width: 100%;">
                </label>
                <button type="submit" class="primary" style="width: 100%; text-align: center; margin-top: 6px; padding: 14px;">Enter CampusVerse ↗</button>
            </form>
        </div>

        <div style="max-width: 600px; width: 100%; margin: 28px auto 0; text-align: center;">
            <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;">
                <div class="panel" style="padding: 14px; text-align: left;">
                    <b style="font-size: 11px; display: block; color: var(--purple);">▤ Study Chat</b>
                    <small style="color: var(--muted); font-size: 10px;">4 Subject Rooms</small>
                </div>
                <div class="panel" style="padding: 14px; text-align: left;">
                    <b style="font-size: 11px; display: block; color: #ac7450;">✳ VibeMatch</b>
                    <small style="color: var(--muted); font-size: 10px;">Jaccard Similarity</small>
                </div>
                <div class="panel" style="padding: 14px; text-align: left;">
                    <b style="font-size: 11px; display: block; color: #5e7b32;">♧ GhostRooms</b>
                    <small style="color: var(--muted); font-size: 10px;">Ephemeral Chats</small>
                </div>
                <div class="panel" style="padding: 14px; text-align: left;">
                    <b style="font-size: 11px; display: block; color: #6586bb;">◈ DoubtDrop</b>
                    <small style="color: var(--muted); font-size: 10px;">Peer Academic Q&amp;A</small>
                </div>
            </div>
        </div>
    </div>
<% } else { %>
    <!-- Authenticated Student View -->
    <aside class="sidebar">
        <a class="brand" href="#home" onclick="navigate('home')">
            <span class="brand-icon">c<span>✳</span></span>
            <strong>campusverse<small>L I T E</small></strong>
        </a>
        <div class="campus">
            <span class="campus-icon">▥</span>
            <div>Chandigarh University<small>Your campus community</small></div>
            <span class="online-dot"></span>
        </div>
        <div class="nav-label">YOUR SPACE</div>
        <nav id="nav"></nav>
        <div class="sidebar-bottom">
            <div class="note">
                <span>✳</span>
                <h3>Good things happen<br>when we connect.</h3>
                <p>Your next study buddy is<br>probably one hello away.</p>
            </div>
            <div style="display:flex; align-items:center; justify-content:space-between; gap:6px;">
                <button class="profile-button" onclick="navigate('profile')" aria-label="Edit your profile" style="flex:1;">
                    <span class="avatar lime" id="sidebar-avatar"><%= initials %></span>
                    <span><b id="user-name"><%= SecurityUtils.escapeHtml(studentName) %></b><small>Edit your profile ↗</small></span>
                </button>
                <form action="${pageContext.request.contextPath}/student" method="post" style="margin:0;">
                    <input type="hidden" name="action" value="logout">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
                    <button type="submit" class="room-action" style="font-size:9px; padding:6px 8px;" title="Switch user / Log out">Exit</button>
                </form>
            </div>
        </div>
    </aside>

    <div class="shell">
        <header>
            <div class="breadcrumb">Your campus <span>/</span> <b id="crumb">Overview</b></div>
            <div class="header-right">
                <span class="demo" id="status-tag">CAMPUSVERSE LITE</span>
                <button class="icon-button" aria-label="View saved activity" onclick="navigate('saved')">♧</button>
                <button class="avatar purple" id="header-avatar" onclick="navigate('profile')" aria-label="Edit profile"><%= initials %></button>
            </div>
        </header>

        <main id="main"></main>

        <footer>
            <span>Made for the between-class moments.</span>
            <span>CampusVerse Lite <span class="footer-star">✳</span> Connect. Learn. Collaborate.</span>
        </footer>
    </div>

    <dialog id="dialog">
        <button class="close" aria-label="Close dialog" onclick="this.closest('dialog').close()">×</button>
        <div id="dialog-body"></div>
    </dialog>

    <div id="toast" role="status"></div>

    <script>
        window.APP_CONTEXT = '${pageContext.request.contextPath}';
        window.CSRF_TOKEN = '<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>';
        window.INITIAL_STUDENT = {
            id: <%= currentStudent.getId() %>,
            name: '<%= SecurityUtils.escapeJson(studentName) %>'
        };
    </script>
    <script src="${pageContext.request.contextPath}/js/app.js"></script>
<% } %>
</body>
</html>

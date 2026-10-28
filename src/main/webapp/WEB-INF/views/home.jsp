<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    String studentName = (currentStudent != null && currentStudent.getNickname() != null)
            ? currentStudent.getNickname().replace('_', ' ')
            : "";
    String initials = "ST";
    if (!studentName.isEmpty()) {
        String[] parts = studentName.split("\\s+");
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
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
    <title>campusverse LITE · Chandigarh University</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>✳</text></svg>">
    <style>
        .login-box {
            max-width: 480px;
            margin: 60px auto;
            background: #fff;
            border: 1px solid var(--line);
            border-radius: 16px;
            padding: 36px 32px;
            box-shadow: 0 12px 40px #2419340f;
        }
        .login-box h2 {
            font-size: 24px;
            margin: 0 0 8px;
            letter-spacing: -0.6px;
        }
        .login-box p {
            color: var(--muted);
            font-size: 13px;
            line-height: 1.7;
            margin: 0 0 24px;
        }
        .form-input {
            width: 100%;
            padding: 13px 15px;
            font-size: 14px;
            border: 1px solid #dcd7e3;
            border-radius: 9px;
            margin-bottom: 18px;
        }
        .form-input:focus {
            outline: 2px solid #a88deb;
        }
        .login-submit-btn {
            width: 100%;
            padding: 14px;
            background: var(--purple);
            color: #fff;
            border-radius: 9px;
            font-weight: 600;
            font-size: 13px;
            text-align: center;
        }
        .login-submit-btn:hover {
            background: #6340ca;
        }
        .alert-box {
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 12px;
            line-height: 1.6;
        }
        .alert-err {
            background: #fee2e2;
            color: #991b1b;
            border: 1px solid #fecaca;
        }
        .alert-ok {
            background: #edf7e7;
            color: #3f6212;
            border: 1px solid #d9f0c7;
        }
    </style>
</head>
<body>

<% if (currentStudent == null) { %>
    <!-- Unauthenticated Nickname Entry Flow -->
    <div style="min-height: 100vh; display: flex; flex-direction: column; justify-content: center; padding: 20px;">
        <div style="text-align: center; margin-bottom: 10px;">
            <div class="brand-icon" style="display:inline-block; margin-bottom: 8px;">c<span class="star">✳</span></div>
            <div style="font-family:Manrope,sans-serif; font-size:24px; font-weight:800; letter-spacing:-1px;">campusverse <span style="font-size:12px; font-weight:600; color:var(--purple); letter-spacing:3px;">LITE</span></div>
            <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Chandigarh University · Student Collaboration Platform</div>
        </div>

        <div class="login-box">
            <% if (request.getParameter("error") != null) { %>
                <div class="alert-box alert-err"><%= SecurityUtils.escapeHtml(request.getParameter("error")) %></div>
            <% } %>
            <% if (request.getParameter("msg") != null) { %>
                <div class="alert-box alert-ok"><%= SecurityUtils.escapeHtml(request.getParameter("msg")) %></div>
            <% } %>

            <h2>Enter your campus corner</h2>
            <p>Pick a student nickname to start collaborating. No password or complex setup required.</p>

            <form action="${pageContext.request.contextPath}/student" method="post">
                <input type="hidden" name="action" value="login">
                <input type="hidden" name="csrfToken" value="<%= csrfToken != null ? SecurityUtils.escapeHtml(csrfToken) : "" %>">
                <label style="font-size: 11px; font-weight: 700; color: #5a5563; display: block; margin-bottom: 8px;">STUDENT NICKNAME</label>
                <input type="text" name="nickname" class="form-input" placeholder="e.g. AlexDev, CodeNinja, Priya_J"
                       required maxlength="50" autocomplete="off" autofocus>
                <button type="submit" class="login-submit-btn">Enter CampusVerse ↗</button>
            </form>
        </div>

        <div style="max-width: 600px; margin: 0 auto; text-align: center;">
            <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 10px;">
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
    <!-- Authenticated Student CampusVerse Experience -->
    <aside class="sidebar">
        <div class="brand">
            <div class="brand-icon">c<span class="star">✳</span></div>
            <div>
                <strong>campusverse</strong>
                <small>LITE</small>
            </div>
        </div>

        <div class="campus">
            <span class="symbol">▥</span>
            <div>
                <b>Chandigarh University</b>
                <small><span class="online-dot"></span> Your campus community</small>
            </div>
        </div>

        <div class="nav-label">EXPLORE</div>
        <nav id="nav">
            <!-- Dynamically populated by JS router -->
        </nav>

        <div class="sidebar-bottom">
            <div class="note">
                <span>✳</span>
                <h3>A quiet campus corner.</h3>
                <p>Questions get answered. Strangers become project partners. Be kind, always.</p>
            </div>

            <button class="profile-button" onclick="navigate('profile')" aria-label="Edit Profile">
                <span class="avatar lime"><%= initials %></span>
                <div style="flex:1;overflow:hidden">
                    <b id="user-name"><%= SecurityUtils.escapeHtml(studentName) %></b>
                    <small>Tap to edit profile</small>
                </div>
                <span style="color:var(--muted);font-size:12px">↗</span>
            </button>
        </div>
    </aside>

    <div class="shell">
        <header>
            <div class="breadcrumb">
                campusverse <span>/</span> <b id="crumb">Overview</b>
            </div>
            <div class="header-right">
                <span class="demo">CHANDIGARH UNIVERSITY · LITE</span>
                <button onclick="navigate('profile')" style="border:0;padding:0;background:none;" aria-label="Profile">
                    <span class="avatar lime header-avatar"><%= initials %></span>
                </button>
            </div>
        </header>

        <main id="main">
            <!-- Dynamic view injected by app.js -->
        </main>

        <footer>
            <div>
                <b>campusverse LITE</b> · Chandigarh University edition
                <p style="margin:4px 0 0">Built for students who want to study better, together.</p>
            </div>
            <div>
                <a href="#home" onclick="navigate('home')">Overview</a> ·
                <a href="#chat" onclick="navigate('chat')">Study Chat</a> ·
                <a href="#match" onclick="navigate('match')">VibeMatch</a> ·
                <a href="#ghost" onclick="navigate('ghost')">GhostRooms</a> ·
                <a href="#doubts" onclick="navigate('doubts')">DoubtDrop</a>
            </div>
        </footer>
    </div>

    <!-- Native Dialog Modal -->
    <dialog id="dialog">
        <button class="close" onclick="$('#dialog').close()" aria-label="Close dialog">✕</button>
        <div id="dialog-body"></div>
    </dialog>

    <!-- Toast Notification -->
    <div id="toast" role="alert"></div>

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

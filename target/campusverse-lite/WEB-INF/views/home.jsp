<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Student" %>
<%
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    String studentName = (currentStudent != null && currentStudent.getNickname() != null)
            ? currentStudent.getNickname().replace('_', ' ')
            : "Milee Kesarwani";
    String initials = "MK";
    if (studentName.length() >= 2) {
        String[] parts = studentName.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        } else {
            initials = studentName.substring(0, Math.min(2, studentName.length())).toUpperCase();
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>campusverse LITE · Chandigarh University</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>✳</text></svg>">
</head>
<body>

    <!-- Sidebar -->
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
                    <b id="user-name"><%= studentName %></b>
                    <small>Tap to edit profile</small>
                </div>
                <span style="color:var(--muted);font-size:12px">↗</span>
            </button>
        </div>
    </aside>

    <!-- Main Shell -->
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

    <!-- Initial Server State & Script -->
    <script>
        window.APP_CONTEXT = '${pageContext.request.contextPath}';
        window.INITIAL_STUDENT = {
            id: <%= currentStudent != null ? currentStudent.getId() : "null" %>,
            name: '<%= studentName %>'
        };
    </script>
    <script src="${pageContext.request.contextPath}/js/app.js"></script>
</body>
</html>

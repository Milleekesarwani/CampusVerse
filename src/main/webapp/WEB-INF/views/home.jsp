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
            : "Milee Kesarwani";

    String initials = "MK";
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
            <button class="profile-button" onclick="navigate('profile')" aria-label="Edit your profile">
                <span class="avatar lime" id="sidebar-avatar"><%= initials %></span>
                <span><b id="user-name"><%= SecurityUtils.escapeHtml(studentName) %></b><small>Edit your profile ↗</small></span>
            </button>
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
            id: <%= currentStudent != null ? currentStudent.getId() : 1 %>,
            name: '<%= SecurityUtils.escapeJson(studentName) %>'
        };
    </script>
    <script src="${pageContext.request.contextPath}/js/app.js"></script>
</body>
</html>

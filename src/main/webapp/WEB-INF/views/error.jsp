<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" isErrorPage="true" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%
    int statusCode = response.getStatus();
    if (statusCode < 400) {
        Object codeObj = request.getAttribute("jakarta.servlet.error.status_code");
        if (codeObj instanceof Integer) {
            statusCode = (Integer) codeObj;
        } else {
            statusCode = 500;
        }
    }

    String title = "Something went wrong";
    String description = "An unexpected error occurred while processing your request.";
    if (statusCode == 404) {
        title = "Page Not Found";
        description = "The campus page or resource you are looking for doesn't exist or has moved.";
    } else if (statusCode == 403) {
        title = "Access Denied";
        description = "You do not have permission to view or perform this action.";
    } else if (statusCode == 429) {
        title = "Too Many Requests";
        description = "Too many attempts. Please slow down and try again in a few minutes.";
    }
%>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title><%= statusCode %> - <%= SecurityUtils.escapeHtml(title) %> · CampusVerse</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>✳</text></svg>">
</head>
<body>
    <div style="min-height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 24px;">
        <div style="text-align: center; margin-bottom: 24px;">
            <div class="brand-icon" style="display:inline-block; margin-bottom: 8px;">c<span>✳</span></div>
            <div style="font-family:Manrope,sans-serif; font-size:26px; font-weight:800; letter-spacing:-1px;">campusverse <span style="font-size:12px; font-weight:600; color:var(--purple); letter-spacing:3px;">LITE</span></div>
            <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Chandigarh University · Student Collaboration Platform</div>
        </div>

        <div class="panel" style="max-width: 480px; width: 100%; padding: 36px 32px; box-shadow: 0 12px 40px #2419340f; border-radius: 16px; text-align: center;">
            <div style="font-size: 48px; font-weight: 800; color: var(--purple, #6366f1); margin-bottom: 12px; line-height: 1;"><%= statusCode %></div>
            <h2 style="margin: 0 0 10px; font-size: 20px; font-weight: 700;"><%= SecurityUtils.escapeHtml(title) %></h2>
            <p style="color: var(--muted, #64748b); font-size: 14px; line-height: 1.6; margin: 0 0 24px;"><%= SecurityUtils.escapeHtml(description) %></p>
            <a href="${pageContext.request.contextPath}/home" class="btn btn-primary" style="display: inline-block; padding: 10px 24px; text-decoration: none; border-radius: 8px; font-weight: 600;">Back to CampusVerse</a>
        </div>
    </div>
</body>
</html>


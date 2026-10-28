<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.campusverse.model.Room" %>
<%@ page import="com.campusverse.model.Message" %>
<%@ page import="com.campusverse.model.Student" %>
<%@ page import="com.campusverse.util.SecurityUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    Room room = (Room) request.getAttribute("room");
    List<Message> history = (List<Message>) request.getAttribute("history");
    Student currentStudent = (Student) request.getAttribute("currentStudent");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    boolean isExpired = room != null && room.isExpired();
    long maxId = 0L;
    if (history != null && !history.isEmpty()) {
        maxId = history.get(history.size() - 1).getId();
    }
    String studentName = currentStudent != null ? SecurityUtils.escapeHtml(currentStudent.getNickname()) : "Student";
    String roomName = room != null ? SecurityUtils.escapeHtml(room.getName()) : "Chat";
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
    <title><%= roomName %> - CampusVerse Lite</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .chat-box {
            display: flex;
            flex-direction: column;
            height: 65vh;
            border: 1px solid var(--border-color);
            background: white;
            border-radius: 8px;
            overflow: hidden;
        }
        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 1rem;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }
        .message-item {
            display: flex;
            flex-direction: column;
            max-width: 75%;
            padding: 0.6rem 0.85rem;
            border-radius: 8px;
            background: #f1f5f9;
        }
        .message-mine {
            align-self: flex-end;
            background: #dbeafe;
        }
        .message-header {
            display: flex;
            justify-content: space-between;
            font-size: 0.75rem;
            margin-bottom: 0.25rem;
            color: var(--text-muted);
            gap: 1rem;
        }
        .message-sender {
            font-weight: 700;
            color: var(--primary-dark);
        }
        .message-text {
            word-break: break-word;
            font-size: 0.95rem;
        }
        .chat-input-bar {
            display: flex;
            padding: 0.75rem;
            background: #f8fafc;
            border-top: 1px solid var(--border-color);
            gap: 0.5rem;
        }
        .chat-input-bar input {
            flex: 1;
            padding: 0.6rem 0.8rem;
            border: 1px solid var(--border-color);
            border-radius: 6px;
        }
        #chatError {
            display: none;
            padding: 0.6rem 1rem;
            background: #fee2e2;
            color: #b91c1c;
            border-bottom: 1px solid #fca5a5;
            font-size: 0.85rem;
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
                <a href="${pageContext.request.contextPath}/ghostrooms">GhostRooms</a>
            </nav>
            <div class="user-badge">
                <span>👤 <strong><%= studentName %></strong></span>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-sm btn-outline">Exit Room</a>
            </div>
        </div>
    </header>

    <main class="container">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 1rem;">
            <div>
                <h2>💬 <%= roomName %></h2>
                <span class="badge <%= isExpired ? "badge-closed" : "badge-open" %>">
                    <%= isExpired ? "CLOSED / Expired" : "ACTIVE" %>
                </span>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-secondary">&larr; Back to Dashboard</a>
            </div>
        </div>

        <% if (request.getParameter("error") != null) { %>
            <div class="alert alert-danger"><%= SecurityUtils.escapeHtml(request.getParameter("error")) %></div>
        <% } %>
        <% if (request.getParameter("msg") != null) { %>
            <div class="alert alert-success"><%= SecurityUtils.escapeHtml(request.getParameter("msg")) %></div>
        <% } %>

        <div class="chat-box">
            <div id="chatError"></div>

            <div class="chat-messages" id="chatMessages">
                <% if (history != null && !history.isEmpty()) {
                    for (Message m : history) {
                        boolean isMine = currentStudent != null && currentStudent.getId().equals(m.getStudentId());
                %>
                    <div class="message-item <%= isMine ? "message-mine" : "" %>" data-id="<%= m.getId() %>">
                        <div class="message-header">
                            <span class="message-sender"><%= SecurityUtils.escapeHtml(m.getSenderNickname()) %></span>
                            <span class="message-time"><%= m.getCreatedAt() != null ? sdf.format(m.getCreatedAt()) : "" %></span>
                        </div>
                        <div class="message-text"><%= SecurityUtils.escapeHtml(m.getContent()) %></div>
                    </div>
                <%  }
                   } else { %>
                    <p id="emptyMsg" style="text-align: center; color: var(--text-muted); margin: auto;">No messages yet. Start the study discussion!</p>
                <% } %>
            </div>

            <% if (!isExpired) { %>
                <form id="chatForm" class="chat-input-bar">
                    <input type="text" id="contentInput" placeholder="Type your message..." maxlength="2000" autocomplete="off" required>
                    <button type="submit" class="btn btn-primary" id="sendBtn">Send</button>
                </form>
            <% } else { %>
                <div style="padding: 1rem; text-align: center; background: #fee2e2; color: #991b1b; font-weight: 600;">
                    🔒 This room has expired and is closed to new messages.
                </div>
            <% } %>
        </div>
    </main>

    <script>
        const roomId = <%= room != null ? room.getId() : 0 %>;
        let lastMessageId = <%= maxId %>;
        const currentStudentId = <%= currentStudent != null ? currentStudent.getId() : 0 %>;
        const chatContainer = document.getElementById("chatMessages");
        const chatForm = document.getElementById("chatForm");
        const contentInput = document.getElementById("contentInput");
        const sendBtn = document.getElementById("sendBtn");
        const chatError = document.getElementById("chatError");
        const contextPath = "${pageContext.request.contextPath}";
        const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content || "";

        function showError(msg) {
            if (!chatError) return;
            chatError.textContent = msg;
            chatError.style.display = "block";
            setTimeout(() => { chatError.style.display = "none"; }, 5000);
        }

        function scrollToBottom() {
            if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
        }
        scrollToBottom();

        // Send message via AJAX POST - preserves draft on failure
        if (chatForm) {
            chatForm.addEventListener("submit", async function(e) {
                e.preventDefault();
                const content = contentInput.value.trim();
                if (!content) return;

                sendBtn.disabled = true;

                try {
                    const bodyParams = new URLSearchParams();
                    bodyParams.append("roomId", roomId);
                    bodyParams.append("content", content);
                    bodyParams.append("csrfToken", csrfToken);

                    const res = await fetch(contextPath + "/messages", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded",
                            "Accept": "application/json",
                            "X-Requested-With": "XMLHttpRequest",
                            "X-CSRF-Token": csrfToken
                        },
                        body: bodyParams.toString()
                    });

                    if (res.ok) {
                        contentInput.value = ""; // Clear draft ONLY on success
                        pollMessages();
                    } else {
                        const err = await res.json().catch(() => ({}));
                        showError(err.error || "Could not send message. Please try again.");
                    }
                } catch (err) {
                    showError("Network connection error. Your draft was preserved.");
                } finally {
                    sendBtn.disabled = false;
                }
            });
        }

        // Poll messages roughly every 3 seconds using textContent
        async function pollMessages() {
            try {
                const res = await fetch(contextPath + "/api/messages?roomId=" + roomId + "&afterId=" + lastMessageId);
                if (!res.ok) return;

                const messages = await res.json();
                if (messages && messages.length > 0) {
                    const emptyMsg = document.getElementById("emptyMsg");
                    if (emptyMsg) emptyMsg.remove();

                    messages.forEach(msg => {
                        if (msg.id > lastMessageId) {
                            lastMessageId = msg.id;

                            const div = document.createElement("div");
                            div.className = "message-item" + (msg.studentId === currentStudentId ? " message-mine" : "");
                            div.setAttribute("data-id", msg.id);

                            const header = document.createElement("div");
                            header.className = "message-header";

                            const sender = document.createElement("span");
                            sender.className = "message-sender";
                            sender.textContent = msg.senderNickname;

                            const time = document.createElement("span");
                            time.className = "message-time";
                            time.textContent = msg.createdAt;

                            header.appendChild(sender);
                            header.appendChild(time);

                            const body = document.createElement("div");
                            body.className = "message-text";
                            body.textContent = msg.content; // Strict textContent rendering

                            div.appendChild(header);
                            div.appendChild(body);

                            chatContainer.appendChild(div);
                        }
                    });
                    scrollToBottom();
                }
            } catch (err) {
                // Polling error handled gracefully
            }
        }

        const pollTimer = setInterval(pollMessages, 3000);
        window.addEventListener("beforeunload", () => clearInterval(pollTimer));
    </script>
</body>
</html>

package com.campusverse.servlet;

import com.campusverse.model.Message;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.ChatService;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "ChatServlet", urlPatterns = {"/chat", "/messages"})
public class ChatServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ChatServlet.class.getName());
    private final ChatService chatService = new ChatService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        if ("true".equalsIgnoreCase(request.getParameter("rooms"))) {
            response.setContentType("application/json;charset=UTF-8");
            try {
                List<Room> rooms = chatService.getStudyRooms();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < rooms.size(); i++) {
                    Room r = rooms.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(r.getId())
                      .append(",\"name\":\"").append(SecurityUtils.escapeJson(r.getName())).append("\"")
                      .append(",\"type\":\"").append(SecurityUtils.escapeJson(r.getType())).append("\"")
                      .append(",\"status\":\"").append(SecurityUtils.escapeJson(r.getStatus())).append("\"}");
                }
                sb.append("]");
                response.getWriter().write(sb.toString());
            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Database error loading rooms\"}");
            }
            return;
        }

        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;
        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/home?error=Please+enter+a+nickname+first");
            return;
        }

        String roomIdParam = request.getParameter("roomId");
        if (roomIdParam == null || roomIdParam.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/dashboard?error=Room+ID+required");
            return;
        }

        try {
            Long roomId = Long.parseLong(roomIdParam.trim());
            Room room = chatService.getRoom(roomId);
            if (room == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Study room not found");
                return;
            }

            List<Message> history = chatService.getRecentMessages(roomId, 50);

            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"roomId\":").append(room.getId()).append(",");
                sb.append("\"name\":\"").append(SecurityUtils.escapeJson(room.getName())).append("\",");
                sb.append("\"type\":\"").append(SecurityUtils.escapeJson(room.getType())).append("\",");
                sb.append("\"status\":\"").append(SecurityUtils.escapeJson(room.getStatus())).append("\",");
                sb.append("\"messages\":[");
                for (int i = 0; i < history.size(); i++) {
                    Message m = history.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(m.getId())
                      .append(",\"sender\":\"").append(SecurityUtils.escapeJson(m.getSenderNickname())).append("\"")
                      .append(",\"content\":\"").append(SecurityUtils.escapeJson(m.getContent())).append("\"")
                      .append(",\"time\":\"").append(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "").append("\"}");
                }
                sb.append("]}");
                response.getWriter().write(sb.toString());
                return;
            }

            request.setAttribute("room", room);
            request.setAttribute("history", history);
            request.setAttribute("currentStudent", current);

            request.getRequestDispatcher("/WEB-INF/views/chat.jsp").forward(request, response);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid room ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error loading chat room", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        // Strictly enforce server-side session identity
        if (current == null || current.getId() == null) {
            sendErrorResponse(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Active session required");
            return;
        }

        String roomIdParam = request.getParameter("roomId");
        String content = request.getParameter("content");

        if (roomIdParam == null || roomIdParam.trim().isEmpty()) {
            sendErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST, "Room ID is required");
            return;
        }

        try {
            Long roomId = Long.parseLong(roomIdParam.trim());
            Message saved = chatService.postMessage(roomId, current.getId(), content);

            // Respond based on request type
            String accept = request.getHeader("Accept");
            String xRequestedWith = request.getHeader("X-Requested-With");
            if ((accept != null && accept.contains("application/json")) || "XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(String.format(
                        "{\"success\":true,\"id\":%d,\"messageId\":%d,\"sender\":\"%s\"}",
                        saved.getId(),
                        saved.getId(),
                        SecurityUtils.escapeJson(current.getNickname())
                ));
            } else {
                // Standard POST/Redirect/GET
                response.sendRedirect(request.getContextPath() + "/chat?roomId=" + roomId);
            }

        } catch (NumberFormatException e) {
            sendErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST, "Invalid room ID");
        } catch (IllegalArgumentException e) {
            sendErrorResponse(request, response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            sendErrorResponse(request, response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist chat message", e);
            sendErrorResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private void sendErrorResponse(HttpServletRequest req, HttpServletResponse resp, int status, String message)
            throws IOException {
        String accept = req.getHeader("Accept");
        String xRequestedWith = req.getHeader("X-Requested-With");
        if ((accept != null && accept.contains("application/json")) || "XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
            resp.setStatus(status);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write(String.format("{\"error\":\"%s\"}", SecurityUtils.escapeJson(message)));
        } else {
            String roomId = req.getParameter("roomId");
            if (roomId != null) {
                resp.sendRedirect(req.getContextPath() + "/chat?roomId=" + roomId + "&error=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                resp.sendError(status, message);
            }
        }
    }
}


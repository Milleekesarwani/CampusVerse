package com.campusverse.servlet;

import com.campusverse.model.Message;
import com.campusverse.service.ChatService;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "MessageApiServlet", urlPatterns = {"/api/messages"})
public class MessageApiServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(MessageApiServlet.class.getName());
    private final ChatService chatService = new ChatService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String roomIdParam = request.getParameter("roomId");
        String afterIdParam = request.getParameter("afterId");

        if (roomIdParam == null || roomIdParam.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"roomId parameter is required\"}");
            return;
        }

        try {
            Long roomId = Long.parseLong(roomIdParam.trim());
            Long afterId = 0L;
            if (afterIdParam != null && !afterIdParam.trim().isEmpty()) {
                afterId = Long.parseLong(afterIdParam.trim());
            }

            List<Message> messages = chatService.getMessagesAfter(roomId, afterId);

            StringBuilder json = new StringBuilder("[");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(m.getId()).append(",")
                    .append("\"roomId\":").append(m.getRoomId()).append(",")
                    .append("\"studentId\":").append(m.getStudentId()).append(",")
                    .append("\"senderNickname\":\"").append(SecurityUtils.escapeJson(m.getSenderNickname())).append("\",")
                    .append("\"content\":\"").append(SecurityUtils.escapeJson(m.getContent())).append("\",")
                    .append("\"createdAt\":\"").append(m.getCreatedAt() != null ? sdf.format(m.getCreatedAt()) : "").append("\"")
                    .append("}");
            }
            json.append("]");

            response.getWriter().write(json.toString());

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid roomId or afterId parameter\"}");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying messages API", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}


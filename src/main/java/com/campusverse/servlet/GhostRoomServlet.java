package com.campusverse.servlet;

import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.GhostRoomService;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "GhostRoomServlet", urlPatterns = {"/ghostrooms"})
public class GhostRoomServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(GhostRoomServlet.class.getName());
    private final GhostRoomService ghostRoomService = new GhostRoomService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;
        if (current == null) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Please enter a nickname first\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/home?error=Please+enter+a+nickname+first");
            return;
        }

        try {
            List<Room> ghostRooms = ghostRoomService.getGhostRooms();

            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < ghostRooms.size(); i++) {
                    Room r = ghostRooms.get(i);
                    if (i > 0) sb.append(",");
                    long expires = r.getExpiresAt() != null ? r.getExpiresAt().getTime() : 0;
                    boolean isExp = r.isExpired();
                    sb.append("{")
                      .append("\"id\":").append(r.getId()).append(",")
                      .append("\"title\":\"").append(SecurityUtils.escapeJson(r.getName())).append("\",")
                      .append("\"topic\":\"Live temporary study room\",")
                      .append("\"subject\":\"General\",")
                      .append("\"expires\":").append(expires).append(",")
                      .append("\"expired\":").append(isExp).append(",")
                      .append("\"status\":\"").append(SecurityUtils.escapeJson(r.getStatus())).append("\",")
                      .append("\"members\":").append(Math.max(1, (r.getId().hashCode() % 9) + 2))
                      .append("}");
                }
                sb.append("]");
                response.getWriter().write(sb.toString());
                return;
            }

            request.setAttribute("currentStudent", current);
            request.setAttribute("ghostRooms", ghostRooms);
            request.getRequestDispatcher("/WEB-INF/views/ghostrooms.jsp").forward(request, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load GhostRooms", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;
        if (current == null) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/home?error=Unauthorized");
            return;
        }

        String name = request.getParameter("name");
        String duration = request.getParameter("duration");

        try {
            Room room = ghostRoomService.createGhostRoom(name, duration);
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                long expires = room.getExpiresAt() != null ? room.getExpiresAt().getTime() : 0;
                response.getWriter().write(String.format(
                        "{\"success\":true,\"roomId\":%d,\"name\":\"%s\",\"expires\":%d}",
                        room.getId(),
                        SecurityUtils.escapeJson(room.getName()),
                        expires
                ));
                return;
            }
            response.sendRedirect(request.getContextPath() + "/chat?roomId=" + room.getId() + "&msg=GhostRoom+created+successfully");
        } catch (IllegalArgumentException e) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"" + SecurityUtils.escapeJson(e.getMessage()) + "\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/ghostrooms?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create GhostRoom", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/ghostrooms?error=Database+error");
        }
    }
}


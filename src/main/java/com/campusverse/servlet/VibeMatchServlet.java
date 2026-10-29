package com.campusverse.servlet;

import com.campusverse.dao.RoomDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.MatchResult;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.service.VibeMatchService;
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

@WebServlet(name = "VibeMatchServlet", urlPatterns = {"/vibematch"})
public class VibeMatchServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(VibeMatchServlet.class.getName());
    private final VibeMatchService vibeMatchService = new VibeMatchService();
    private final RoomDAO roomDAO = new RoomDAO();

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
            List<MatchResult> matches = vibeMatchService.findMatches(current.getId());
            List<Room> studyRooms = roomDAO.findStudyRooms();

            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < matches.size(); i++) {
                    MatchResult m = matches.get(i);
                    Student peer = m.getPeer();
                    String peerNick = peer.getNickname() != null ? peer.getNickname().replace('_', ' ') : "Peer";
                    String initials = peerNick.length() >= 2 ? peerNick.substring(0, 2).toUpperCase() : peerNick.toUpperCase();

                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"id\":").append(peer.getId()).append(",")
                      .append("\"nickname\":\"").append(SecurityUtils.escapeJson(peer.getNickname())).append("\",")
                      .append("\"name\":\"").append(SecurityUtils.escapeJson(peerNick)).append("\",")
                      .append("\"initials\":\"").append(SecurityUtils.escapeJson(initials)).append("\",")
                      .append("\"score\":").append(m.getMatchPercentage()).append(",")
                      .append("\"matchPercentage\":").append(m.getMatchPercentage()).append(",")
                      .append("\"year\":\"CSE · 2nd year\",")
                      .append("\"bio\":\"Building things. Learning together on CampusVerse.\",")
                      .append("\"interests\":[");

                    List<Interest> peerInterests = peer.getInterests();
                    if (peerInterests != null) {
                        for (int j = 0; j < peerInterests.size(); j++) {
                            if (j > 0) sb.append(",");
                            sb.append("\"").append(SecurityUtils.escapeJson(peerInterests.get(j).getName())).append("\"");
                        }
                    }
                    sb.append("],\"sharedInterests\":[");
                    int k = 0;
                    for (String shared : m.getSharedInterests()) {
                        if (k > 0) sb.append(",");
                        sb.append("\"").append(SecurityUtils.escapeJson(shared)).append("\"");
                        k++;
                    }
                    sb.append("]}");
                }
                sb.append("]");
                response.getWriter().write(sb.toString());
                return;
            }

            request.setAttribute("currentStudent", current);
            request.setAttribute("matches", matches);
            request.setAttribute("studyRooms", studyRooms);

            request.getRequestDispatcher("/WEB-INF/views/vibematch.jsp").forward(request, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading VibeMatch recommendations", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }
}


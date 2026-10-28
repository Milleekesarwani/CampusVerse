package com.campusverse.servlet;

import com.campusverse.model.Doubt;
import com.campusverse.model.DoubtAnswer;
import com.campusverse.model.Student;
import com.campusverse.service.DoubtService;
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

@WebServlet(name = "DoubtServlet", urlPatterns = {"/doubts"})
public class DoubtServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DoubtServlet.class.getName());
    private final DoubtService doubtService = new DoubtService();

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

        String idParam = request.getParameter("id");
        if (idParam != null && !idParam.trim().isEmpty()) {
            try {
                Long id = Long.parseLong(idParam.trim());
                Doubt doubt = doubtService.getDoubtById(id);
                if (doubt == null) {
                    if (isJson) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"Doubt not found\"}");
                        return;
                    }
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Doubt not found");
                    return;
                }

                if (isJson) {
                    response.setContentType("application/json;charset=UTF-8");
                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"id\":").append(doubt.getId()).append(",");
                    sb.append("\"subject\":\"").append(SecurityUtils.escapeJson(doubt.getSubject())).append("\",");
                    sb.append("\"title\":\"").append(SecurityUtils.escapeJson(doubt.getTitle())).append("\",");
                    sb.append("\"body\":\"").append(SecurityUtils.escapeJson(doubt.getDescription())).append("\",");
                    sb.append("\"author\":\"").append(SecurityUtils.escapeJson(doubt.getAuthorNickname())).append("\",");
                    sb.append("\"studentId\":").append(doubt.getStudentId()).append(",");
                    sb.append("\"status\":\"").append(SecurityUtils.escapeJson(doubt.getStatus())).append("\",");
                    sb.append("\"resolved\":").append("RESOLVED".equalsIgnoreCase(doubt.getStatus())).append(",");
                    sb.append("\"owned\":").append(current.getId() != null && current.getId().equals(doubt.getStudentId())).append(",");
                    sb.append("\"replies\":[");
                    List<DoubtAnswer> answers = doubt.getAnswers();
                    if (answers != null) {
                        for (int i = 0; i < answers.size(); i++) {
                            DoubtAnswer a = answers.get(i);
                            if (i > 0) sb.append(",");
                            sb.append("{\"id\":").append(a.getId())
                              .append(",\"author\":\"").append(SecurityUtils.escapeJson(a.getAuthorNickname())).append("\"")
                              .append(",\"text\":\"").append(SecurityUtils.escapeJson(a.getAnswerText())).append("\"")
                              .append(",\"createdAt\":\"").append(a.getCreatedAt() != null ? a.getCreatedAt().toString() : "").append("\"}");
                        }
                    }
                    sb.append("]}");
                    response.getWriter().write(sb.toString());
                    return;
                }

                request.setAttribute("doubt", doubt);
                request.setAttribute("currentStudent", current);
                request.getRequestDispatcher("/WEB-INF/views/doubt_detail.jsp").forward(request, response);
                return;
            } catch (NumberFormatException e) {
                if (isJson) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Invalid doubt ID\"}");
                    return;
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid doubt ID");
                return;
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error loading doubt details", e);
                if (isJson) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Database error\"}");
                    return;
                }
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
                return;
            }
        }

        // List view with optional subject filter
        String subject = request.getParameter("subject");
        try {
            List<Doubt> doubts = doubtService.getDoubts(subject);

            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < doubts.size(); i++) {
                    Doubt d = doubts.get(i);
                    // Fetch replies to know reply count
                    Doubt detailed = doubtService.getDoubtById(d.getId());
                    List<DoubtAnswer> replies = (detailed != null) ? detailed.getAnswers() : List.of();
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"id\":").append(d.getId()).append(",")
                      .append("\"subject\":\"").append(SecurityUtils.escapeJson(d.getSubject())).append("\",")
                      .append("\"title\":\"").append(SecurityUtils.escapeJson(d.getTitle())).append("\",")
                      .append("\"body\":\"").append(SecurityUtils.escapeJson(d.getDescription())).append("\",")
                      .append("\"author\":\"").append(SecurityUtils.escapeJson(d.getAuthorNickname())).append("\",")
                      .append("\"studentId\":").append(d.getStudentId()).append(",")
                      .append("\"status\":\"").append(SecurityUtils.escapeJson(d.getStatus())).append("\",")
                      .append("\"resolved\":").append("RESOLVED".equalsIgnoreCase(d.getStatus())).append(",")
                      .append("\"owned\":").append(current.getId() != null && current.getId().equals(d.getStudentId())).append(",")
                      .append("\"repliesCount\":").append(replies != null ? replies.size() : 0).append(",")
                      .append("\"replies\":[");
                    if (replies != null) {
                        for (int j = 0; j < replies.size(); j++) {
                            DoubtAnswer a = replies.get(j);
                            if (j > 0) sb.append(",");
                            sb.append("{\"id\":").append(a.getId())
                              .append(",\"author\":\"").append(SecurityUtils.escapeJson(a.getAuthorNickname())).append("\"")
                              .append(",\"text\":\"").append(SecurityUtils.escapeJson(a.getAnswerText())).append("\"}");
                        }
                    }
                    sb.append("]}");
                }
                sb.append("]");
                response.getWriter().write(sb.toString());
                return;
            }

            request.setAttribute("doubts", doubts);
            request.setAttribute("currentSubject", subject != null ? subject : "ALL");
            request.setAttribute("currentStudent", current);
            request.getRequestDispatcher("/WEB-INF/views/doubts.jsp").forward(request, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading doubts list", e);
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
        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;
        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/home?error=Unauthorized");
            return;
        }

        String action = request.getParameter("action");
        if ("resolve".equalsIgnoreCase(action)) {
            handleResolve(request, response, current);
        } else {
            handleCreate(request, response, current);
        }
    }

    private void handleCreate(HttpServletRequest request, HttpServletResponse response, Student current)
            throws IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        String subject = request.getParameter("subject");
        String title = request.getParameter("title");
        String description = request.getParameter("description");
        if (description == null || description.trim().isEmpty()) {
            description = request.getParameter("body");
        }

        try {
            Doubt doubt = doubtService.postDoubt(current.getId(), subject, title, description);
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":true,\"id\":" + doubt.getId() + "}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?id=" + doubt.getId() + "&msg=Doubt+posted+successfully");
        } catch (IllegalArgumentException e) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"" + SecurityUtils.escapeJson(e.getMessage()) + "\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?error=" + urlEncode(e.getMessage()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create doubt", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?error=Database+error");
        }
    }

    private void handleResolve(HttpServletRequest request, HttpServletResponse response, Student current)
            throws IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        String idParam = request.getParameter("doubtId");
        if (idParam == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing doubtId");
            return;
        }

        try {
            Long doubtId = Long.parseLong(idParam.trim());
            doubtService.resolveDoubt(doubtId, current.getId());
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":true,\"resolved\":true}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?id=" + doubtId + "&msg=Doubt+marked+as+RESOLVED");
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to resolve doubt", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private String urlEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}


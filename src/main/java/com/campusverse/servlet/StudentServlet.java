package com.campusverse.servlet;

import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.Student;
import com.campusverse.util.SecurityFilter;
import com.campusverse.util.SecurityUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentServlet", urlPatterns = {"/student"})
public class StudentServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentServlet.class.getName());
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        if (isJson) {
            response.setContentType("application/json;charset=UTF-8");
            if (current == null) {
                response.getWriter().write("{\"loggedIn\":false}");
                return;
            }
            try {
                List<Interest> myInterests = studentDAO.getStudentInterests(current.getId());
                List<Interest> allInterests = studentDAO.getAllInterests();
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"loggedIn\":true,");
                sb.append("\"id\":").append(current.getId()).append(",");
                sb.append("\"nickname\":\"").append(SecurityUtils.escapeJson(current.getNickname())).append("\",");
                sb.append("\"interests\":[");
                for (int i = 0; i < myInterests.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(SecurityUtils.escapeJson(myInterests.get(i).getName())).append("\"");
                }
                sb.append("],\"allInterests\":[");
                for (int i = 0; i < allInterests.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(allInterests.get(i).getId())
                      .append(",\"name\":\"").append(SecurityUtils.escapeJson(allInterests.get(i).getName())).append("\"}");
                }
                sb.append("]}");
                response.getWriter().write(sb.toString());
                return;
            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
        }

        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        try {
            List<Interest> allInterests = studentDAO.getAllInterests();
            List<Interest> studentInterests = studentDAO.getStudentInterests(current.getId());
            request.setAttribute("allInterests", allInterests);
            request.setAttribute("studentInterests", studentInterests);
            request.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(request, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading student profile", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading profile");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "login";
        }

        switch (action) {
            case "login":
                handleLogin(request, response);
                break;
            case "updateInterests":
                handleUpdateInterests(request, response);
                break;
            case "saveProfile":
            case "update_profile":
                handleSaveProfile(request, response);
                break;
            case "logout":
                handleLogout(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String nickname = request.getParameter("nickname");
        if (nickname == null || nickname.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home?error=Nickname+cannot+be+blank");
            return;
        }

        try {
            Student student = studentDAO.findOrCreateByNickname(nickname);

            // Rotate session ID to protect against session fixation attacks
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                try {
                    request.changeSessionId();
                } catch (Throwable t) {
                    oldSession.invalidate();
                }
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("currentStudent", student);
            session.setAttribute("studentId", student.getId());
            session.setAttribute("nickname", student.getNickname());

            // Generate fresh CSRF token upon authentication
            String newCsrf = UUID.randomUUID().toString();
            session.setAttribute(SecurityFilter.CSRF_TOKEN_ATTR, newCsrf);

            String accept = request.getHeader("Accept");
            boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(String.format("{\"success\":true,\"nickname\":\"%s\",\"csrfToken\":\"%s\"}",
                        SecurityUtils.escapeJson(student.getNickname()), newCsrf));
                return;
            }

            response.sendRedirect(request.getContextPath() + "/home");
        } catch (IllegalArgumentException e) {
            response.sendRedirect(request.getContextPath() + "/home?error=" + urlEncode(e.getMessage()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during student login", e);
            response.sendRedirect(request.getContextPath() + "/home?error=Database+error+occurred");
        }
    }

    private void handleUpdateInterests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;
        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/home?error=Session+expired");
            return;
        }

        String[] selected = request.getParameterValues("interestIds");
        List<Long> interestIds = new ArrayList<>();
        if (selected != null) {
            for (String s : selected) {
                try {
                    interestIds.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            studentDAO.setStudentInterests(current.getId(), interestIds);
            current.setInterests(studentDAO.getStudentInterests(current.getId()));
            session.setAttribute("currentStudent", current);

            String accept = request.getHeader("Accept");
            boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":true}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/dashboard?msg=Interests+updated+successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update student interests", e);
            response.sendRedirect(request.getContextPath() + "/dashboard?error=Failed+to+update+interests");
        }
    }

    private void handleSaveProfile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String accept = request.getHeader("Accept");
        boolean isJson = "true".equalsIgnoreCase(request.getParameter("json")) || (accept != null && accept.contains("application/json"));

        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        String rawNickname = request.getParameter("nickname");
        if (rawNickname == null || rawNickname.trim().isEmpty()) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Nickname cannot be blank\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/home?error=Nickname+cannot+be+blank");
            }
            return;
        }

        String nickname = rawNickname.trim();
        String sanitizedNick = nickname.replaceAll("\\s+", "_");

        try {
            Student student;
            if (current != null) {
                if (!sanitizedNick.equalsIgnoreCase(current.getNickname())) {
                    Student existingWithNick = studentDAO.findByNickname(sanitizedNick);
                    if (existingWithNick != null && !existingWithNick.getId().equals(current.getId())) {
                        if (isJson) {
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"Nickname already taken by another student\"}");
                        } else {
                            response.sendRedirect(request.getContextPath() + "/home?error=Nickname+already+taken");
                        }
                        return;
                    }
                    studentDAO.updateNickname(current.getId(), sanitizedNick);
                    current.setNickname(sanitizedNick);
                }
                student = studentDAO.findById(current.getId());
            } else {
                student = studentDAO.findOrCreateByNickname(sanitizedNick);
            }

            if (session == null) {
                session = request.getSession(true);
            }
            session.setAttribute("currentStudent", student);
            session.setAttribute("studentId", student.getId());
            session.setAttribute("nickname", rawNickname.trim());

            String[] interestNames = request.getParameterValues("interests");
            List<Long> matchedIds = new ArrayList<>();
            if (interestNames != null) {
                List<Interest> allInterests = studentDAO.getAllInterests();
                for (String inName : interestNames) {
                    if (inName == null || inName.trim().isEmpty()) continue;
                    for (Interest item : allInterests) {
                        if (item.getName().equalsIgnoreCase(inName.trim())) {
                            matchedIds.add(item.getId());
                            break;
                        }
                    }
                }
            }

            // Save interests (clearing all interests when none provided)
            studentDAO.setStudentInterests(student.getId(), matchedIds);
            student.setInterests(studentDAO.getStudentInterests(student.getId()));
            session.setAttribute("currentStudent", student);

            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":true,\"nickname\":\"" + SecurityUtils.escapeJson(rawNickname.trim()) + "\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/home?msg=Profile+saved+successfully");
            }
        } catch (IllegalArgumentException e) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"" + SecurityUtils.escapeJson(e.getMessage()) + "\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/home?error=" + urlEncode(e.getMessage()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save profile", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Failed to save profile\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/home?error=Failed+to+save+profile");
            }
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/home?msg=Logged+out+successfully");
    }

    private String urlEncode(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }
}


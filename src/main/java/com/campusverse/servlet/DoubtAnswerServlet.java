package com.campusverse.servlet;

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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "DoubtAnswerServlet", urlPatterns = {"/doubts/answer"})
public class DoubtAnswerServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DoubtAnswerServlet.class.getName());
    private final DoubtService doubtService = new DoubtService();

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

        String doubtIdParam = request.getParameter("doubtId");
        String answerText = request.getParameter("answerText");
        if (answerText == null || answerText.trim().isEmpty()) {
            answerText = request.getParameter("reply");
        }
        if (answerText == null || answerText.trim().isEmpty()) {
            answerText = request.getParameter("content");
        }

        if (doubtIdParam == null || doubtIdParam.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing doubtId");
            return;
        }

        try {
            Long doubtId = Long.parseLong(doubtIdParam.trim());
            doubtService.postAnswer(doubtId, current.getId(), answerText);
            if (isJson) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":true,\"doubtId\":" + doubtId + "}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?id=" + doubtId + "&msg=Answer+added+successfully");
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid doubtId");
        } catch (IllegalArgumentException e) {
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"" + SecurityUtils.escapeJson(e.getMessage()) + "\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?id=" + doubtIdParam + "&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error adding doubt answer", e);
            if (isJson) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/doubts?id=" + doubtIdParam + "&error=Database+error");
        }
    }
}


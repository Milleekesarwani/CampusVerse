package com.campusverse.servlet;

import com.campusverse.model.Student;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Entry point servlet for CampusVerse Lite.
 * Mapped to /home and the root path.
 */
@WebServlet(name = "HomeServlet", urlPatterns = {"/home", ""})
public class HomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        request.setAttribute("appName", "campusverse LITE");
        request.setAttribute("tagline", "A little community. A lot of possibility.");
        request.setAttribute("currentStudent", current);
        request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
    }
}

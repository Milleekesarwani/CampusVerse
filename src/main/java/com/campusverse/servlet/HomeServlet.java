package com.campusverse.servlet;

import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.Student;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point servlet for CampusVerse Lite.
 * Mapped to /home and the root path.
 */
@WebServlet(name = "HomeServlet", urlPatterns = {"/home", ""})
public class HomeServlet extends HttpServlet {

    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        Student current = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        if (current == null) {
            try {
                current = studentDAO.findOrCreateByNickname("Milee_Kesarwani");
                List<Interest> all = studentDAO.getAllInterests();
                List<Long> defaultInterestIds = new ArrayList<>();
                for (Interest item : all) {
                    if ("Java".equalsIgnoreCase(item.getName()) || "DSA".equalsIgnoreCase(item.getName()) || "Hackathons".equalsIgnoreCase(item.getName())) {
                        defaultInterestIds.add(item.getId());
                    }
                }
                if (!defaultInterestIds.isEmpty()) {
                    studentDAO.setStudentInterests(current.getId(), defaultInterestIds);
                    current.setInterests(studentDAO.getStudentInterests(current.getId()));
                }
                session.setAttribute("currentStudent", current);
                session.setAttribute("studentId", current.getId());
                session.setAttribute("nickname", "Milee Kesarwani");
            } catch (Exception ignored) {
            }
        }

        request.setAttribute("appName", "campusverse LITE");
        request.setAttribute("tagline", "A little community. A lot of possibility.");
        request.setAttribute("currentStudent", current);
        request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
    }
}


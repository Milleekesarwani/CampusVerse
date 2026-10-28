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
import java.sql.SQLException;
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
                Student defaultStudent = studentDAO.findOrCreateByNickname("Milee_Kesarwani");
                List<Interest> interests = studentDAO.getStudentInterests(defaultStudent.getId());
                if (interests == null || interests.isEmpty()) {
                    List<Interest> all = studentDAO.getAllInterests();
                    List<Long> matched = all.stream()
                        .filter(i -> "Java".equalsIgnoreCase(i.getName()) || "DSA".equalsIgnoreCase(i.getName()) || "Hackathons".equalsIgnoreCase(i.getName()))
                        .map(Interest::getId)
                        .toList();
                    studentDAO.setStudentInterests(defaultStudent.getId(), matched);
                    defaultStudent.setInterests(studentDAO.getStudentInterests(defaultStudent.getId()));
                } else {
                    defaultStudent.setInterests(interests);
                }
                current = defaultStudent;
                if (session != null) {
                    session.setAttribute("currentStudent", current);
                    session.setAttribute("studentId", current.getId());
                    session.setAttribute("nickname", "Milee Kesarwani");
                }
            } catch (SQLException ignored) {
            }
        }

        request.setAttribute("appName", "campusverse LITE");
        request.setAttribute("tagline", "A little community. A lot of possibility.");
        request.setAttribute("currentStudent", current);
        request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
    }
}

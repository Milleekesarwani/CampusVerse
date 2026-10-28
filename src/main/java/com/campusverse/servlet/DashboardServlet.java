package com.campusverse.servlet;

import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.Room;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "DashboardServlet", urlPatterns = {"/dashboard"})
public class DashboardServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DashboardServlet.class.getName());
    private final RoomDAO roomDAO = new RoomDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Student student = (session != null) ? (Student) session.getAttribute("currentStudent") : null;

        if (student == null) {
            response.sendRedirect(request.getContextPath() + "/home?error=Please+enter+a+nickname+first");
            return;
        }

        try {
            // Fresh student state from DB
            Student updatedStudent = studentDAO.findById(student.getId());
            if (updatedStudent != null) {
                student = updatedStudent;
                session.setAttribute("currentStudent", student);
            }

            List<Room> studyRooms = roomDAO.findStudyRooms();
            List<Interest> allInterests = studentDAO.getAllInterests();
            List<Interest> studentInterests = studentDAO.getStudentInterests(student.getId());

            request.setAttribute("student", student);
            request.setAttribute("studyRooms", studyRooms);
            request.setAttribute("allInterests", allInterests);
            request.setAttribute("studentInterests", studentInterests);

            request.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(request, response);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load dashboard data", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error loading dashboard");
        }
    }
}


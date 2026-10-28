package com.campusverse.dao;

import com.campusverse.model.Interest;
import com.campusverse.model.Student;
import com.campusverse.util.DatabaseManager;

import java.sql.*;
import java.util.*;

public class StudentDAO {

    public Student findOrCreateByNickname(String rawNickname) throws SQLException {
        if (rawNickname == null) {
            throw new IllegalArgumentException("Nickname cannot be null");
        }
        String nickname = rawNickname.trim();
        if (nickname.isEmpty() || nickname.length() > 50 || !nickname.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid nickname. Must be 1-50 alphanumeric characters (or _.-)");
        }

        Student existing = findByNickname(nickname);
        if (existing != null) {
            return existing;
        }

        String insertSql = "INSERT INTO students (nickname) VALUES (?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nickname);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    return findById(id);
                }
            }
        }
        return findByNickname(nickname);
    }

    public Student findById(Long id) throws SQLException {
        if (id == null) return null;
        String sql = "SELECT id, nickname, created_at FROM students WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getTimestamp("created_at")
                    );
                    student.setInterests(getStudentInterests(student.getId()));
                    return student;
                }
            }
        }
        return null;
    }

    public Student findByNickname(String nickname) throws SQLException {
        if (nickname == null || nickname.trim().isEmpty()) return null;
        String sql = "SELECT id, nickname, created_at FROM students WHERE LOWER(nickname) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nickname.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getTimestamp("created_at")
                    );
                    student.setInterests(getStudentInterests(student.getId()));
                    return student;
                }
            }
        }
        return null;
    }

    public List<Interest> getAllInterests() throws SQLException {
        List<Interest> list = new ArrayList<>();
        String sql = "SELECT id, name FROM interests ORDER BY name ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new Interest(rs.getLong("id"), rs.getString("name")));
            }
        }
        return list;
    }

    public List<Interest> getStudentInterests(Long studentId) throws SQLException {
        List<Interest> list = new ArrayList<>();
        if (studentId == null) return list;
        String sql = """
            SELECT i.id, i.name
            FROM interests i
            INNER JOIN student_interests si ON i.id = si.interest_id
            WHERE si.student_id = ?
            ORDER BY i.name ASC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Interest(rs.getLong("id"), rs.getString("name")));
                }
            }
        }
        return list;
    }

    public void setStudentInterests(Long studentId, List<Long> interestIds) throws SQLException {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID cannot be null");
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete current associations
                try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM student_interests WHERE student_id = ?")) {
                    delStmt.setLong(1, studentId);
                    delStmt.executeUpdate();
                }

                // Insert distinct new associations
                if (interestIds != null && !interestIds.isEmpty()) {
                    Set<Long> uniqueIds = new HashSet<>(interestIds);
                    String insertSql = "INSERT INTO student_interests (student_id, interest_id) VALUES (?, ?)";
                    try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                        for (Long interestId : uniqueIds) {
                            if (interestId != null) {
                                insStmt.setLong(1, studentId);
                                insStmt.setLong(2, interestId);
                                insStmt.addBatch();
                            }
                        }
                        insStmt.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Student> getAllStudents() throws SQLException {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT id, nickname, created_at FROM students ORDER BY id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Student s = new Student(
                        rs.getLong("id"),
                        rs.getString("nickname"),
                        rs.getTimestamp("created_at")
                );
                s.setInterests(getStudentInterests(s.getId()));
                students.add(s);
            }
        }
        return students;
    }
}


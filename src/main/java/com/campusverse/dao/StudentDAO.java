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

        String salt = com.campusverse.util.PasswordUtils.generateSalt();
        String hash = com.campusverse.util.PasswordUtils.hashPassword(com.campusverse.util.PasswordUtils.DEFAULT_DEMO_PASSWORD, salt);

        String insertSql = "INSERT INTO students (nickname, password_hash, password_salt) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nickname);
            stmt.setString(2, hash);
            stmt.setString(3, salt);
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

    public Student register(String rawNickname, String rawPassword) throws SQLException {
        if (rawNickname == null || rawNickname.trim().isEmpty()) {
            throw new IllegalArgumentException("Nickname cannot be blank");
        }
        String nickname = rawNickname.trim().replaceAll("\\s+", "_");
        if (nickname.length() < 2 || nickname.length() > 50 || !nickname.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Nickname must be 2-50 characters (letters, numbers, _.-)");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (rawPassword.length() > 100) {
            throw new IllegalArgumentException("Password cannot exceed 100 characters");
        }

        Student existing = findByNickname(nickname);
        if (existing != null) {
            throw new IllegalArgumentException("Nickname already taken by another student");
        }

        String salt = com.campusverse.util.PasswordUtils.generateSalt();
        String hash = com.campusverse.util.PasswordUtils.hashPassword(rawPassword, salt);

        String insertSql = "INSERT INTO students (nickname, password_hash, password_salt) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nickname);
            stmt.setString(2, hash);
            stmt.setString(3, salt);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return findById(rs.getLong(1));
                }
            }
        }
        return findByNickname(nickname);
    }

    public Student authenticate(String rawNickname, String rawPassword) throws SQLException {
        if (rawNickname == null || rawNickname.trim().isEmpty() || rawPassword == null) {
            return null;
        }
        String nickname = rawNickname.trim().replaceAll("\\s+", "_");
        Student student = findByNickname(nickname);
        if (student == null) {
            return null;
        }

        if (student.getPasswordHash() == null || student.getPasswordHash().isEmpty()) {
            if (com.campusverse.util.PasswordUtils.DEFAULT_DEMO_PASSWORD.equals(rawPassword) || rawPassword.length() >= 6) {
                String salt = com.campusverse.util.PasswordUtils.generateSalt();
                String hash = com.campusverse.util.PasswordUtils.hashPassword(rawPassword, salt);
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("UPDATE students SET password_hash = ?, password_salt = ? WHERE id = ?")) {
                    stmt.setString(1, hash);
                    stmt.setString(2, salt);
                    stmt.setLong(3, student.getId());
                    stmt.executeUpdate();
                }
                student.setPasswordHash(hash);
                student.setPasswordSalt(salt);
                return student;
            }
            return null;
        }

        if (com.campusverse.util.PasswordUtils.verifyPassword(rawPassword, student.getPasswordHash(), student.getPasswordSalt())) {
            return student;
        }
        return null;
    }

    public Student findById(Long id) throws SQLException {
        if (id == null) return null;
        String sql = "SELECT id, nickname, password_hash, password_salt, created_at FROM students WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getString("password_hash"),
                            rs.getString("password_salt"),
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
        String sql = "SELECT id, nickname, password_hash, password_salt, created_at FROM students WHERE LOWER(nickname) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nickname.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student student = new Student(
                            rs.getLong("id"),
                            rs.getString("nickname"),
                            rs.getString("password_hash"),
                            rs.getString("password_salt"),
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

    public void updateNickname(Long studentId, String rawNickname) throws SQLException {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID cannot be null");
        }
        if (rawNickname == null) {
            throw new IllegalArgumentException("Nickname cannot be null");
        }
        String nickname = rawNickname.trim();
        if (nickname.isEmpty() || nickname.length() > 50 || !nickname.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid nickname. Must be 1-50 alphanumeric characters (or _.-)");
        }
        String sql = "UPDATE students SET nickname = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.setLong(2, studentId);
            stmt.executeUpdate();
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


package com.campusverse.dao;

import com.campusverse.model.Doubt;
import com.campusverse.model.DoubtAnswer;
import com.campusverse.util.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DoubtDAO {

    public Doubt createDoubt(Doubt doubt) throws SQLException {
        if (doubt == null || doubt.getStudentId() == null) {
            throw new IllegalArgumentException("Author student ID required");
        }
        String sql = "INSERT INTO doubts (student_id, subject, title, description, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = doubt.getCreatedAt() != null ? doubt.getCreatedAt() : new Timestamp(System.currentTimeMillis());
            stmt.setLong(1, doubt.getStudentId());
            stmt.setString(2, doubt.getSubject());
            stmt.setString(3, doubt.getTitle());
            stmt.setString(4, doubt.getDescription());
            stmt.setString(5, doubt.getStatus() != null ? doubt.getStatus() : "OPEN");
            stmt.setTimestamp(6, now);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    doubt.setId(rs.getLong(1));
                    doubt.setCreatedAt(now);
                    return doubt;
                }
            }
        }
        return doubt;
    }

    public Doubt findById(Long id) throws SQLException {
        if (id == null) return null;
        String sql = """
            SELECT d.id, d.student_id, s.nickname, d.subject, d.title, d.description, d.status, d.created_at
            FROM doubts d
            INNER JOIN students s ON d.student_id = s.id
            WHERE d.id = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Doubt doubt = mapResultSetToDoubt(rs);
                    doubt.setAnswers(getAnswersForDoubt(doubt.getId()));
                    return doubt;
                }
            }
        }
        return null;
    }

    public List<Doubt> findDoubts(String subjectFilter) throws SQLException {
        List<Doubt> list = new ArrayList<>();
        boolean hasFilter = subjectFilter != null && !subjectFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(subjectFilter);

        String sql = """
            SELECT d.id, d.student_id, s.nickname, d.subject, d.title, d.description, d.status, d.created_at
            FROM doubts d
            INNER JOIN students s ON d.student_id = s.id
        """ + (hasFilter ? " WHERE LOWER(d.subject) = LOWER(?) " : "") + " ORDER BY d.created_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (hasFilter) {
                stmt.setString(1, subjectFilter.trim());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDoubt(rs));
                }
            }
        }
        return list;
    }

    public DoubtAnswer addAnswer(DoubtAnswer answer) throws SQLException {
        if (answer == null || answer.getDoubtId() == null || answer.getStudentId() == null) {
            throw new IllegalArgumentException("Doubt ID and Student ID are required");
        }
        String sql = "INSERT INTO doubt_answers (doubt_id, student_id, answer_text, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = answer.getCreatedAt() != null ? answer.getCreatedAt() : new Timestamp(System.currentTimeMillis());
            stmt.setLong(1, answer.getDoubtId());
            stmt.setLong(2, answer.getStudentId());
            stmt.setString(3, answer.getAnswerText());
            stmt.setTimestamp(4, now);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    answer.setId(rs.getLong(1));
                    answer.setCreatedAt(now);
                    return answer;
                }
            }
        }
        return answer;
    }

    public List<DoubtAnswer> getAnswersForDoubt(Long doubtId) throws SQLException {
        List<DoubtAnswer> list = new ArrayList<>();
        if (doubtId == null) return list;
        String sql = """
            SELECT a.id, a.doubt_id, a.student_id, s.nickname, a.answer_text, a.created_at
            FROM doubt_answers a
            INNER JOIN students s ON a.student_id = s.id
            WHERE a.doubt_id = ?
            ORDER BY a.created_at ASC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, doubtId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new DoubtAnswer(
                            rs.getLong("id"),
                            rs.getLong("doubt_id"),
                            rs.getLong("student_id"),
                            rs.getString("nickname"),
                            rs.getString("answer_text"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Resolves a doubt ONLY if the requesting student is the original author.
     * Enforced at the SQL query level!
     */
    public boolean markResolved(Long doubtId, Long currentStudentId) throws SQLException {
        if (doubtId == null || currentStudentId == null) return false;
        String sql = "UPDATE doubts SET status = 'RESOLVED' WHERE id = ? AND student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, doubtId);
            stmt.setLong(2, currentStudentId);
            int updated = stmt.executeUpdate();
            return updated > 0;
        }
    }

    private Doubt mapResultSetToDoubt(ResultSet rs) throws SQLException {
        return new Doubt(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("nickname"),
                rs.getString("subject"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
    }
}


package com.campusverse.dao;

import com.campusverse.model.Message;
import com.campusverse.util.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageDAO {

    public Message saveMessage(Message message) throws SQLException {
        if (message == null || message.getRoomId() == null || message.getStudentId() == null) {
            throw new IllegalArgumentException("Room ID and Student ID are required");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be blank");
        }

        String sql = "INSERT INTO messages (room_id, student_id, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            Timestamp now = message.getCreatedAt() != null ? message.getCreatedAt() : new Timestamp(System.currentTimeMillis());
            stmt.setLong(1, message.getRoomId());
            stmt.setLong(2, message.getStudentId());
            stmt.setString(3, message.getContent().trim());
            stmt.setTimestamp(4, now);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    message.setId(rs.getLong(1));
                    message.setCreatedAt(now);
                    return message;
                }
            }
        }
        return message;
    }

    public List<Message> getRecentMessages(Long roomId, int limit) throws SQLException {
        List<Message> list = new ArrayList<>();
        if (roomId == null) return list;

        int maxRows = Math.min(Math.max(limit, 1), 200);
        String sql = """
            SELECT m.id, m.room_id, m.student_id, s.nickname, m.content, m.created_at
            FROM messages m
            INNER JOIN students s ON m.student_id = s.id
            WHERE m.room_id = ?
            ORDER BY m.id DESC
            LIMIT ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, roomId);
            stmt.setInt(2, maxRows);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMessage(rs));
                }
            }
        }
        Collections.reverse(list); // Chronological order
        return list;
    }

    public List<Message> getMessagesAfter(Long roomId, Long afterId) throws SQLException {
        List<Message> list = new ArrayList<>();
        if (roomId == null) return list;
        long cursor = (afterId != null) ? afterId : 0L;

        String sql = """
            SELECT m.id, m.room_id, m.student_id, s.nickname, m.content, m.created_at
            FROM messages m
            INNER JOIN students s ON m.student_id = s.id
            WHERE m.room_id = ? AND m.id > ?
            ORDER BY m.id ASC
            LIMIT 100
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, roomId);
            stmt.setLong(2, cursor);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMessage(rs));
                }
            }
        }
        return list;
    }

    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        return new Message(
                rs.getLong("id"),
                rs.getLong("room_id"),
                rs.getLong("student_id"),
                rs.getString("nickname"),
                rs.getString("content"),
                rs.getTimestamp("created_at")
        );
    }
}


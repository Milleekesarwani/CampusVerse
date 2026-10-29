package com.campusverse.dao;

import com.campusverse.model.Room;
import com.campusverse.util.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoomDAO {

    public List<Room> findStudyRooms() throws SQLException {
        List<Room> list = new ArrayList<>();
        String sql = "SELECT id, name, type, topic, subject, created_at, expires_at, status FROM rooms WHERE type = 'STUDY' ORDER BY id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToRoom(rs));
            }
        }
        return list;
    }

    public Room findById(Long id) throws SQLException {
        if (id == null) return null;
        String sql = "SELECT id, name, type, topic, subject, created_at, expires_at, status FROM rooms WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRoom(rs);
                }
            }
        }
        return null;
    }

    public List<Room> findAllGhostRooms() throws SQLException {
        List<Room> list = new ArrayList<>();
        String sql = "SELECT id, name, type, topic, subject, created_at, expires_at, status FROM rooms WHERE type = 'GHOST' ORDER BY created_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToRoom(rs));
            }
        }
        return list;
    }

    public Room createRoom(Room room) throws SQLException {
        if (room == null || room.getName() == null || room.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Room name cannot be empty");
        }
        String sql = "INSERT INTO rooms (name, type, topic, subject, created_at, expires_at, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, room.getName().trim());
            stmt.setString(2, room.getType() != null ? room.getType() : "STUDY");
            stmt.setString(3, room.getTopic());
            stmt.setString(4, room.getSubject() != null ? room.getSubject() : "General");
            Timestamp now = room.getCreatedAt() != null ? room.getCreatedAt() : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(5, now);
            stmt.setTimestamp(6, room.getExpiresAt());
            stmt.setString(7, room.getStatus() != null ? room.getStatus() : "ACTIVE");

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    room.setId(rs.getLong(1));
                    room.setCreatedAt(now);
                    return room;
                }
            }
        }
        return room;
    }

    public void updateStatus(Long roomId, String status) throws SQLException {
        if (roomId == null) return;
        String sql = "UPDATE rooms SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, roomId);
            stmt.executeUpdate();
        }
    }

    private Room mapResultSetToRoom(ResultSet rs) throws SQLException {
        String topic = null;
        String subject = null;
        try {
            topic = rs.getString("topic");
        } catch (SQLException ignored) {}
        try {
            subject = rs.getString("subject");
        } catch (SQLException ignored) {}

        return new Room(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type"),
                topic,
                subject,
                rs.getTimestamp("created_at"),
                rs.getTimestamp("expires_at"),
                rs.getString("status")
        );
    }
}

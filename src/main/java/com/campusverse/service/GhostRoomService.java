package com.campusverse.service;

import com.campusverse.dao.RoomDAO;
import com.campusverse.model.Room;
import com.campusverse.util.SecurityUtils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class GhostRoomService {

    private final RoomDAO roomDAO;
    private final Clock clock;

    public GhostRoomService() {
        this(new RoomDAO(), Clock.systemUTC());
    }

    public GhostRoomService(RoomDAO roomDAO, Clock clock) {
        this.roomDAO = roomDAO;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public Room createGhostRoom(String rawName, String durationStr) throws SQLException {
        return createGhostRoom(rawName, durationStr, null, "General");
    }

    public Room createGhostRoom(String rawName, String durationStr, String topic, String subject) throws SQLException {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new IllegalArgumentException("Room name cannot be blank");
        }
        String name = rawName.trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("Room name cannot exceed 100 characters");
        }

        Duration duration = parseDuration(durationStr);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(duration);

        Room room = new Room();
        room.setName(SecurityUtils.escapeHtml(name));
        room.setType("GHOST");
        room.setTopic(topic != null && !topic.trim().isEmpty() ? SecurityUtils.escapeHtml(topic.trim()) : null);
        room.setSubject(subject != null && !subject.trim().isEmpty() ? SecurityUtils.escapeHtml(subject.trim()) : "General");
        room.setCreatedAt(Timestamp.from(now));
        room.setExpiresAt(Timestamp.from(expiresAt));
        room.setStatus("ACTIVE");

        return roomDAO.createRoom(room);
    }

    public List<Room> getGhostRooms() throws SQLException {
        List<Room> rooms = roomDAO.findAllGhostRooms();
        Instant now = clock.instant();

        for (Room r : rooms) {
            if ("ACTIVE".equalsIgnoreCase(r.getStatus()) && r.getExpiresAt() != null) {
                if (!now.isBefore(r.getExpiresAt().toInstant())) {
                    r.setStatus("CLOSED");
                    roomDAO.updateStatus(r.getId(), "CLOSED");
                }
            }
        }
        return rooms;
    }

    public boolean isRoomExpired(Room room) {
        if (room == null) return true;
        if ("CLOSED".equalsIgnoreCase(room.getStatus())) return true;
        if (room.getExpiresAt() != null) {
            return !clock.instant().isBefore(room.getExpiresAt().toInstant());
        }
        return false;
    }

    public Duration parseDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Duration.ofMinutes(5); // default
        }
        String s = input.trim().toLowerCase();

        // 5 minutes
        if (s.contains("5 min") || s.equals("5m") || s.equals("5") || s.equals("5m revision") || s.contains("5-minute")) {
            return Duration.ofMinutes(5);
        }
        // 1 hour
        if (s.contains("1 hour") || s.equals("1h") || s.equals("60m") || s.equals("1") || s.contains("1 hr")) {
            return Duration.ofHours(1);
        }
        // 2 hours - explicitly requested: 2h and "2 hours" mean two hours
        if (s.contains("2 hour") || s.equals("2h") || s.equals("120m") || s.equals("2") || s.contains("2 hr")) {
            return Duration.ofHours(2);
        }
        // 6 hours
        if (s.contains("6 hour") || s.equals("6h") || s.equals("6") || s.contains("6 hr")) {
            return Duration.ofHours(6);
        }
        // 24 hours
        if (s.contains("24 hour") || s.equals("24h") || s.equals("1d") || s.equals("24") || s.contains("24 hr")) {
            return Duration.ofHours(24);
        }

        // Generic patterns: e.g. "2 hours", "3h", "10m"
        if (s.matches("^\\d+\\s*(h|hr|hrs|hours?)$")) {
            long hours = Long.parseLong(s.replaceAll("[^0-9]", ""));
            if (hours >= 1 && hours <= 72) {
                return Duration.ofHours(hours);
            }
        }
        if (s.matches("^\\d+\\s*(m|min|mins|minutes?)$")) {
            long mins = Long.parseLong(s.replaceAll("[^0-9]", ""));
            if (mins >= 1 && mins <= 1440) {
                return Duration.ofMinutes(mins);
            }
        }

        throw new IllegalArgumentException("Unsupported duration: " + input + ". Supported durations: 5m, 1h, 2h, 6h, 24h.");
    }
}


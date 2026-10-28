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
                if (now.isAfter(r.getExpiresAt().toInstant())) {
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
            return clock.instant().isAfter(room.getExpiresAt().toInstant());
        }
        return false;
    }

    private Duration parseDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Duration.ofMinutes(5); // default
        }
        String s = input.trim().toLowerCase();
        if (s.contains("5 min") || s.equals("5m")) {
            return Duration.ofMinutes(5);
        } else if (s.contains("1 hour") || s.equals("1h") || s.equals("60m")) {
            return Duration.ofHours(1);
        } else if (s.contains("6 hour") || s.equals("6h")) {
            return Duration.ofHours(6);
        } else if (s.contains("24 hour") || s.equals("24h") || s.equals("1d")) {
            return Duration.ofHours(24);
        }

        // Test-only / custom numeric minutes
        try {
            long mins = Long.parseLong(s.replaceAll("[^0-9]", ""));
            return Duration.ofMinutes(Math.max(mins, 1));
        } catch (Exception e) {
            return Duration.ofMinutes(5);
        }
    }
}


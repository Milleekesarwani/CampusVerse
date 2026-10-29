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

        // Match minutes: e.g. "5m", "5 min", "5 mins", "5 minutes", "25 minutes", "5-minute", "5m revision"
        java.util.regex.Matcher minMatcher = java.util.regex.Pattern
                .compile("^(\\d+)\\s*(?:-|\\s)?\\s*(?:m|min|mins|minutes?)(?:\\b.*)?$")
                .matcher(s);
        if (minMatcher.matches()) {
            long mins = Long.parseLong(minMatcher.group(1));
            if (mins >= 1 && mins <= 1440) {
                return Duration.ofMinutes(mins);
            }
            throw new IllegalArgumentException("Unsupported duration: " + input + ". Maximum is 24 hours.");
        }

        // Match hours: e.g. "1h", "2h", "2 hours", "12 hours", "21 hours", "6 hr", "24 hrs"
        java.util.regex.Matcher hrMatcher = java.util.regex.Pattern
                .compile("^(\\d+)\\s*(?:-|\\s)?\\s*(?:h|hr|hrs|hours?)(?:\\b.*)?$")
                .matcher(s);
        if (hrMatcher.matches()) {
            long hours = Long.parseLong(hrMatcher.group(1));
            if (hours >= 1 && hours <= 72) {
                return Duration.ofHours(hours);
            }
            throw new IllegalArgumentException("Unsupported duration: " + input + ". Supported range: 1 to 72 hours.");
        }

        // Match days: e.g. "1d", "1 day", "2 days"
        java.util.regex.Matcher dayMatcher = java.util.regex.Pattern
                .compile("^(\\d+)\\s*(?:d|days?)(?:\\b.*)?$")
                .matcher(s);
        if (dayMatcher.matches()) {
            long days = Long.parseLong(dayMatcher.group(1));
            if (days >= 1 && days <= 3) {
                return Duration.ofDays(days);
            }
            throw new IllegalArgumentException("Unsupported duration: " + input + ". Maximum is 3 days.");
        }

        // Match plain numbers: e.g. "5" (5 minutes default), "1", "2", "6", "24" (hours from dropdown values)
        if (s.matches("^\\d+$")) {
            long num = Long.parseLong(s);
            if (num == 5) {
                return Duration.ofMinutes(5);
            }
            if (num >= 1 && num <= 72) {
                return Duration.ofHours(num);
            }
        }

        throw new IllegalArgumentException("Unsupported duration: " + input + ". Supported durations: 5m, 1h, 2h, 6h, 24h.");
    }
}


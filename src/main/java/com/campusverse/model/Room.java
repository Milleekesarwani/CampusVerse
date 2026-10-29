package com.campusverse.model;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a study room or temporary GhostRoom.
 */
public class Room {
    private Long id;
    private String name;
    private String type; // STUDY or GHOST
    private String topic;
    private String subject;
    private Timestamp createdAt;
    private Timestamp expiresAt; // Nullable for STUDY rooms
    private String status; // ACTIVE or CLOSED

    public Room() {}

    public Room(Long id, String name, String type, Timestamp createdAt, Timestamp expiresAt, String status) {
        this(id, name, type, null, null, createdAt, expiresAt, status);
    }

    public Room(Long id, String name, String type, String topic, String subject, Timestamp createdAt, Timestamp expiresAt, String status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.topic = topic;
        this.subject = subject;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Checks if room is expired using the default system UTC clock.
     */
    public boolean isExpired() {
        return isExpired(Clock.systemUTC());
    }

    /**
     * Checks if room is expired against a specified Clock instance.
     * Rejects at or after the expiration instant (now >= expiresAt).
     */
    public boolean isExpired(Clock clock) {
        if ("CLOSED".equalsIgnoreCase(status)) {
            return true;
        }
        if (expiresAt != null) {
            Clock c = clock != null ? clock : Clock.systemUTC();
            return !c.instant().isBefore(expiresAt.toInstant());
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Room{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}

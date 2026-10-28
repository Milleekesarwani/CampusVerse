package com.campusverse.model;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Represents a study room or GhostRoom message.
 */
public class Message {
    private Long id;
    private Long roomId;
    private Long studentId;
    private String senderNickname;
    private String content;
    private Timestamp createdAt;

    public Message() {}

    public Message(Long id, Long roomId, Long studentId, String senderNickname, String content, Timestamp createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.studentId = studentId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public void setSenderNickname(String senderNickname) {
        this.senderNickname = senderNickname;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", roomId=" + roomId +
                ", studentId=" + studentId +
                ", senderNickname='" + senderNickname + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}


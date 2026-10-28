package com.campusverse.service;

import com.campusverse.dao.MessageDAO;
import com.campusverse.dao.RoomDAO;
import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Message;
import com.campusverse.model.Room;
import com.campusverse.model.Student;
import com.campusverse.util.SecurityUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

public class ChatService {

    private final MessageDAO messageDAO;
    private final RoomDAO roomDAO;
    private final StudentDAO studentDAO;
    private final Clock clock;

    public ChatService() {
        this(new MessageDAO(), new RoomDAO(), new StudentDAO(), Clock.systemUTC());
    }

    public ChatService(MessageDAO messageDAO, RoomDAO roomDAO) {
        this(messageDAO, roomDAO, new StudentDAO(), Clock.systemUTC());
    }

    public ChatService(MessageDAO messageDAO, RoomDAO roomDAO, Clock clock) {
        this(messageDAO, roomDAO, new StudentDAO(), clock);
    }

    public ChatService(MessageDAO messageDAO, RoomDAO roomDAO, StudentDAO studentDAO, Clock clock) {
        this.messageDAO = messageDAO;
        this.roomDAO = roomDAO;
        this.studentDAO = studentDAO != null ? studentDAO : new StudentDAO();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Posts a message into a room after performing validation, expiration checks, and XSS sanitization.
     */
    public Message postMessage(Long roomId, Long studentId, String rawContent) throws SQLException {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID is required");
        }
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be blank");
        }
        String trimmed = rawContent.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Message is too long (maximum 2000 characters)");
        }

        // Room validation and GhostRoom expiration check
        Room room = roomDAO.findById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }
        if (room.isExpired(clock)) {
            // Update status if not yet marked CLOSED
            if (!"CLOSED".equalsIgnoreCase(room.getStatus())) {
                roomDAO.updateStatus(roomId, "CLOSED");
            }
            throw new IllegalStateException("Room has expired and is now closed to new messages");
        }

        // Student validation
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("Student not found");
        }

        // Escape HTML to prevent stored XSS attacks
        String safeContent = SecurityUtils.escapeHtml(trimmed);

        Message message = new Message();
        message.setRoomId(roomId);
        message.setStudentId(studentId);
        message.setContent(safeContent);
        message.setSenderNickname(student.getNickname());

        Message saved = messageDAO.saveMessage(message);
        if (saved != null) {
            saved.setSenderNickname(student.getNickname());
        }
        return saved;
    }

    public List<Message> getRecentMessages(Long roomId, int limit) throws SQLException {
        return messageDAO.getRecentMessages(roomId, limit);
    }

    public List<Message> getMessagesAfter(Long roomId, Long afterId) throws SQLException {
        return messageDAO.getMessagesAfter(roomId, afterId);
    }

    public List<Room> getStudyRooms() throws SQLException {
        return roomDAO.findStudyRooms();
    }

    public Room getRoom(Long roomId) throws SQLException {
        return roomDAO.findById(roomId);
    }
}

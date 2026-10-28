package com.campusverse.service;

import com.campusverse.dao.DoubtDAO;
import com.campusverse.model.Doubt;
import com.campusverse.model.DoubtAnswer;
import com.campusverse.util.SecurityUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class DoubtService {

    private static final Set<String> VALID_SUBJECTS = Set.of("Java", "DSA", "DBMS", "General");
    private final DoubtDAO doubtDAO;

    public DoubtService() {
        this.doubtDAO = new DoubtDAO();
    }

    public DoubtService(DoubtDAO doubtDAO) {
        this.doubtDAO = doubtDAO;
    }

    public Doubt postDoubt(Long studentId, String subject, String rawTitle, String rawDesc) throws SQLException {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (subject == null || !VALID_SUBJECTS.contains(subject.trim())) {
            throw new IllegalArgumentException("Invalid subject. Must be one of: " + VALID_SUBJECTS);
        }
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        String title = rawTitle.trim();
        if (title.length() > 200) {
            throw new IllegalArgumentException("Title cannot exceed 200 characters");
        }
        if (rawDesc == null || rawDesc.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }

        Doubt doubt = new Doubt();
        doubt.setStudentId(studentId);
        doubt.setSubject(subject.trim());
        doubt.setTitle(SecurityUtils.escapeHtml(title));
        doubt.setDescription(SecurityUtils.escapeHtml(rawDesc.trim()));
        doubt.setStatus("OPEN");

        return doubtDAO.createDoubt(doubt);
    }

    public DoubtAnswer postAnswer(Long doubtId, Long studentId, String rawAnswer) throws SQLException {
        if (doubtId == null) {
            throw new IllegalArgumentException("Doubt ID is required");
        }
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer cannot be blank");
        }

        Doubt doubt = doubtDAO.findById(doubtId);
        if (doubt == null) {
            throw new IllegalArgumentException("Doubt question not found");
        }

        DoubtAnswer answer = new DoubtAnswer();
        answer.setDoubtId(doubtId);
        answer.setStudentId(studentId);
        answer.setAnswerText(SecurityUtils.escapeHtml(rawAnswer.trim()));

        return doubtDAO.addAnswer(answer);
    }

    public boolean resolveDoubt(Long doubtId, Long currentStudentId) throws SQLException {
        if (doubtId == null || currentStudentId == null) {
            throw new IllegalArgumentException("Doubt ID and Student ID are required");
        }

        Doubt doubt = doubtDAO.findById(doubtId);
        if (doubt == null) {
            throw new IllegalArgumentException("Doubt not found");
        }

        if (!doubt.getStudentId().equals(currentStudentId)) {
            throw new SecurityException("Unauthorized: Only the original author can mark this doubt as resolved");
        }

        return doubtDAO.markResolved(doubtId, currentStudentId);
    }

    public List<Doubt> getDoubts(String subjectFilter) throws SQLException {
        return doubtDAO.findDoubts(subjectFilter);
    }

    public Doubt getDoubtById(Long id) throws SQLException {
        return doubtDAO.findById(id);
    }
}


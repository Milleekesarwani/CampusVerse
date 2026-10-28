package com.campusverse.model;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Represents a peer answer to a Doubt.
 */
public class DoubtAnswer {
    private Long id;
    private Long doubtId;
    private Long studentId;
    private String authorNickname;
    private String answerText;
    private Timestamp createdAt;

    public DoubtAnswer() {}

    public DoubtAnswer(Long id, Long doubtId, Long studentId, String authorNickname, String answerText, Timestamp createdAt) {
        this.id = id;
        this.doubtId = doubtId;
        this.studentId = studentId;
        this.authorNickname = authorNickname;
        this.answerText = answerText;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDoubtId() {
        return doubtId;
    }

    public void setDoubtId(Long doubtId) {
        this.doubtId = doubtId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
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
        DoubtAnswer that = (DoubtAnswer) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}


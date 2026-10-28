package com.campusverse.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an academic question posted in DoubtDrop.
 */
public class Doubt {
    private Long id;
    private Long studentId;
    private String authorNickname;
    private String subject; // Java, DSA, DBMS, General
    private String title;
    private String description;
    private String status; // OPEN or RESOLVED
    private Timestamp createdAt;
    private List<DoubtAnswer> answers = new ArrayList<>();

    public Doubt() {}

    public Doubt(Long id, Long studentId, String authorNickname, String subject, String title, String description, String status, Timestamp createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.authorNickname = authorNickname;
        this.subject = subject;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public List<DoubtAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<DoubtAnswer> answers) {
        this.answers = answers != null ? answers : new ArrayList<>();
    }

    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Doubt doubt = (Doubt) o;
        return Objects.equals(id, doubt.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}


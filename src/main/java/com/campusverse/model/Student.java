package com.campusverse.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a student profile in CampusVerse Lite.
 */
public class Student {
    private Long id;
    private String nickname;
    private String passwordHash;
    private String passwordSalt;
    private Timestamp createdAt;
    private List<Interest> interests = new ArrayList<>();

    public Student() {}

    public Student(Long id, String nickname, Timestamp createdAt) {
        this(id, nickname, null, null, createdAt);
    }

    public Student(Long id, String nickname, String passwordHash, String passwordSalt, Timestamp createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public List<Interest> getInterests() {
        return interests;
    }

    public void setInterests(List<Interest> interests) {
        this.interests = interests != null ? interests : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return Objects.equals(id, student.id) && Objects.equals(nickname, student.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nickname);
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", nickname='" + nickname + '\'' +
                ", createdAt=" + createdAt +
                ", interestsCount=" + (interests != null ? interests.size() : 0) +
                '}';
    }
}


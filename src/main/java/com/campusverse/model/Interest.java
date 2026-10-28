package com.campusverse.model;

import java.util.Objects;

/**
 * Represents an academic or extracurricular interest topic.
 */
public class Interest {
    private Long id;
    private String name;

    public Interest() {}

    public Interest(Long id, String name) {
        this.id = id;
        this.name = name;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interest interest = (Interest) o;
        return Objects.equals(id, interest.id) ||
                (name != null && name.equalsIgnoreCase(interest.name));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : id);
    }

    @Override
    public String toString() {
        return "Interest{id=" + id + ", name='" + name + "'}";
    }
}


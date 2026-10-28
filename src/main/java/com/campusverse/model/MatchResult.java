package com.campusverse.model;

import java.util.Set;

/**
 * Encapsulates the Jaccard similarity match result between the current student and a peer.
 */
public class MatchResult implements Comparable<MatchResult> {
    private final Student peer;
    private final int matchPercentage;
    private final Set<String> sharedInterests;
    private final int unionSize;

    public MatchResult(Student peer, int matchPercentage, Set<String> sharedInterests, int unionSize) {
        this.peer = peer;
        this.matchPercentage = matchPercentage;
        this.sharedInterests = sharedInterests;
        this.unionSize = unionSize;
    }

    public Student getPeer() {
        return peer;
    }

    public int getMatchPercentage() {
        return matchPercentage;
    }

    public Set<String> getSharedInterests() {
        return sharedInterests;
    }

    public int getUnionSize() {
        return unionSize;
    }

    @Override
    public int compareTo(MatchResult other) {
        // Primary sort: descending match percentage
        int cmp = Integer.compare(other.matchPercentage, this.matchPercentage);
        if (cmp != 0) return cmp;

        // Secondary tie-breaker: alphabetical nickname
        String nick1 = this.peer != null && this.peer.getNickname() != null ? this.peer.getNickname() : "";
        String nick2 = other.peer != null && other.peer.getNickname() != null ? other.peer.getNickname() : "";
        int nameCmp = nick1.compareToIgnoreCase(nick2);
        if (nameCmp != 0) return nameCmp;

        // Tertiary tie-breaker: ID
        Long id1 = this.peer != null ? this.peer.getId() : 0L;
        Long id2 = other.peer != null ? other.peer.getId() : 0L;
        return Long.compare(id1, id2);
    }
}


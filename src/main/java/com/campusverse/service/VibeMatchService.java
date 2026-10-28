package com.campusverse.service;

import com.campusverse.dao.StudentDAO;
import com.campusverse.model.Interest;
import com.campusverse.model.MatchResult;
import com.campusverse.model.Student;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class VibeMatchService {

    private final StudentDAO studentDAO;

    public VibeMatchService() {
        this.studentDAO = new StudentDAO();
    }

    public VibeMatchService(StudentDAO studentDAO) {
        this.studentDAO = studentDAO;
    }

    /**
     * Pure Jaccard similarity calculation using Java HashSet.
     * Formula: 100 * |intersection(A,B)| / |union(A,B)|
     */
    public static int calculateJaccardScore(Collection<String> listA, Collection<String> listB) {
        if (listA == null || listB == null || listA.isEmpty() || listB.isEmpty()) {
            return 0;
        }

        // Normalize (lowercase, trimmed) and deduplicate using HashSet
        Set<String> setA = listA.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        Set<String> setB = listB.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (setA.isEmpty() || setB.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 0;
        }

        double ratio = (double) intersection.size() / union.size();
        return (int) Math.round(ratio * 100.0);
    }

    /**
     * Extracts shared original interest names (intersection).
     */
    public static Set<String> findSharedInterests(Collection<Interest> a, Collection<Interest> b) {
        if (a == null || b == null) return Collections.emptySet();

        Set<String> namesA = a.stream().map(Interest::getName).collect(Collectors.toSet());
        return b.stream()
                .map(Interest::getName)
                .filter(namesA::contains)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Finds and ranks matching peers for the given student.
     * Excludes the current student and applies deterministic descending ranking.
     */
    public List<MatchResult> findMatches(Long currentStudentId) throws SQLException {
        if (currentStudentId == null) {
            return Collections.emptyList();
        }

        Student current = studentDAO.findById(currentStudentId);
        if (current == null) {
            return Collections.emptyList();
        }

        List<String> myInterests = current.getInterests().stream()
                .map(Interest::getName)
                .collect(Collectors.toList());

        List<Student> allStudents = studentDAO.getAllStudents();
        List<MatchResult> results = new ArrayList<>();

        for (Student peer : allStudents) {
            // Strictly exclude the current student
            if (peer.getId().equals(currentStudentId)) {
                continue;
            }

            List<String> peerInterests = peer.getInterests().stream()
                    .map(Interest::getName)
                    .collect(Collectors.toList());

            int score = calculateJaccardScore(myInterests, peerInterests);
            Set<String> shared = findSharedInterests(current.getInterests(), peer.getInterests());

            Set<String> union = new HashSet<>(myInterests);
            union.addAll(peerInterests);

            results.add(new MatchResult(peer, score, shared, union.size()));
        }

        // Sort descending by match score with deterministic tie-breaker
        Collections.sort(results);
        return results;
    }
}


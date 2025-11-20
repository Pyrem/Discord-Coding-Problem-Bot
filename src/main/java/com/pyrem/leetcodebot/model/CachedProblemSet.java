package com.pyrem.leetcodebot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing metadata about cached problem sets
 * Tracks when each company-timerange combination was last fetched
 */
@Entity
@Table(name = "cached_problem_sets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedProblemSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the company (normalized)
     */
    @Column(nullable = false)
    private String companyName;

    /**
     * Time range key (e.g., "last30days")
     */
    @Column(nullable = false)
    private String timeRange;

    /**
     * Table name where the problems are stored
     */
    @Column(nullable = false, unique = true)
    private String tableName;

    /**
     * Number of problems in this cached set
     */
    @Column(nullable = false)
    private Integer problemCount;

    /**
     * When this problem set was last fetched/updated
     */
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    /**
     * When this problem set was created
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Check if this cached problem set is expired (older than specified days)
     */
    public boolean isExpired(int expiryDays) {
        return lastUpdated.plusDays(expiryDays).isBefore(LocalDateTime.now());
    }
}

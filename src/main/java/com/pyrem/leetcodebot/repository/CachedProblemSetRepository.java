package com.pyrem.leetcodebot.repository;

import com.pyrem.leetcodebot.model.CachedProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing cached problem set metadata
 */
@Repository
public interface CachedProblemSetRepository extends JpaRepository<CachedProblemSet, Long> {

    /**
     * Find a cached problem set by table name
     */
    Optional<CachedProblemSet> findByTableName(String tableName);

    /**
     * Find a cached problem set by company and time range
     */
    Optional<CachedProblemSet> findByCompanyNameAndTimeRange(String companyName, String timeRange);

    /**
     * Check if a table exists in the cache
     */
    boolean existsByTableName(String tableName);
}

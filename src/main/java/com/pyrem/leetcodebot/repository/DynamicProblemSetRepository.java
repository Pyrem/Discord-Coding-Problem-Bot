package com.pyrem.leetcodebot.repository;

import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.model.ProblemDifficulty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Repository for dynamically creating and managing company-specific problem set tables
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DynamicProblemSetRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Create a new table for storing problems for a specific company and time range
     */
    @Transactional
    public void createProblemSetTable(String tableName) {
        log.info("Creating problem set table: {}", tableName);

        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id SERIAL PRIMARY KEY,
                problem_number INTEGER NOT NULL,
                problem_name VARCHAR(500) NOT NULL,
                acceptance_rate DOUBLE PRECISION,
                difficulty VARCHAR(20),
                frequency DOUBLE PRECISION,
                url VARCHAR(1000),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(problem_number)
            )
            """, tableName);

        jdbcTemplate.execute(sql);
        log.info("Successfully created table: {}", tableName);
    }

    /**
     * Insert problems into a specific table (batch operation)
     */
    @Transactional
    public void saveProblems(String tableName, List<LeetCodeProblem> problems) {
        log.info("Saving {} problems to table: {}", problems.size(), tableName);

        // Clear existing data
        String deleteSql = String.format("DELETE FROM %s", tableName);
        jdbcTemplate.update(deleteSql);

        // Insert new data
        String insertSql = String.format("""
            INSERT INTO %s (problem_number, problem_name, acceptance_rate, difficulty, frequency, url)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (problem_number) DO UPDATE SET
                problem_name = EXCLUDED.problem_name,
                acceptance_rate = EXCLUDED.acceptance_rate,
                difficulty = EXCLUDED.difficulty,
                frequency = EXCLUDED.frequency,
                url = EXCLUDED.url
            """, tableName);

        jdbcTemplate.batchUpdate(insertSql, problems, problems.size(),
            (ps, problem) -> {
                ps.setInt(1, problem.getProblemNumber());
                ps.setString(2, problem.getProblemName());
                ps.setDouble(3, problem.getAcceptanceRate());
                ps.setString(4, problem.getDifficulty().name());
                ps.setDouble(5, problem.getFrequency());
                ps.setString(6, problem.getUrl());
            });

        log.info("Successfully saved {} problems to {}", problems.size(), tableName);
    }

    /**
     * Retrieve all problems from a specific table, ordered by frequency (descending)
     */
    public List<LeetCodeProblem> findAllProblems(String tableName) {
        log.info("Retrieving all problems from table: {}", tableName);

        String sql = String.format("""
            SELECT problem_number, problem_name, acceptance_rate, difficulty, frequency, url
            FROM %s
            ORDER BY frequency DESC, problem_number ASC
            """, tableName);

        return jdbcTemplate.query(sql, new ProblemRowMapper());
    }

    /**
     * Get the count of problems in a specific table
     */
    public int getProblemCount(String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Check if a table exists in the database
     */
    public boolean tableExists(String tableName) {
        String sql = """
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = ?
            )
            """;

        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, tableName);
        return exists != null && exists;
    }

    /**
     * Delete a problem set table (use with caution)
     */
    @Transactional
    public void dropTable(String tableName) {
        log.warn("Dropping table: {}", tableName);
        String sql = String.format("DROP TABLE IF EXISTS %s", tableName);
        jdbcTemplate.execute(sql);
    }

    /**
     * Row mapper for converting database rows to LeetCodeProblem objects
     */
    private static class ProblemRowMapper implements RowMapper<LeetCodeProblem> {
        @Override
        public LeetCodeProblem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return LeetCodeProblem.builder()
                .problemNumber(rs.getInt("problem_number"))
                .problemName(rs.getString("problem_name"))
                .acceptanceRate(rs.getDouble("acceptance_rate"))
                .difficulty(ProblemDifficulty.valueOf(rs.getString("difficulty")))
                .frequency(rs.getDouble("frequency"))
                .url(rs.getString("url"))
                .build();
        }
    }
}

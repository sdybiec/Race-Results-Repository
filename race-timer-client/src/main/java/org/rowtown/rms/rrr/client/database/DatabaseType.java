package org.rowtown.rms.rrr.client.database;

/**
 * Supported database types for local storage.
 *
 * <p>The Race Timer Client supports multiple database engines for local storage,
 * allowing users to choose the best option for their deployment environment.</p>
 *
 * <h2>Database Comparison:</h2>
 * <table border="1">
 *   <tr>
 *     <th>Feature</th>
 *     <th>SQLite</th>
 *     <th>H2</th>
 *   </tr>
 *   <tr>
 *     <td>File-based</td>
 *     <td>Yes</td>
 *     <td>Yes (and in-memory)</td>
 *   </tr>
 *   <tr>
 *     <td>Transaction support</td>
 *     <td>Yes</td>
 *     <td>Yes</td>
 *   </tr>
 *   <tr>
 *     <td>Performance</td>
 *     <td>Fast for reads</td>
 *     <td>Fast for both reads/writes</td>
 *   </tr>
 *   <tr>
 *     <td>Portability</td>
 *     <td>Excellent</td>
 *     <td>Excellent (pure Java)</td>
 *   </tr>
 *   <tr>
 *     <td>Size</td>
 *     <td>Small footprint</td>
 *     <td>Larger (includes JDBC)</td>
 *   </tr>
 *   <tr>
 *     <td>Best for</td>
 *     <td>Mobile, embedded, simple apps</td>
 *     <td>Development, testing, complex queries</td>
 *   </tr>
 * </table>
 *
 * <h2>When to use SQLite:</h2>
 * <ul>
 *   <li>Deploying to embedded devices or mobile platforms</li>
 *   <li>Minimal dependencies required</li>
 *   <li>Simple read-heavy workloads</li>
 *   <li>Maximum portability across platforms</li>
 * </ul>
 *
 * <h2>When to use H2:</h2>
 * <ul>
 *   <li>Development and testing (in-memory mode)</li>
 *   <li>Write-heavy workloads</li>
 *   <li>Complex queries and joins</li>
 *   <li>Pure Java environment (no native libraries)</li>
 *   <li>Need for advanced SQL features</li>
 * </ul>
 */
public enum DatabaseType {
    /**
     * SQLite database engine.
     *
     * <p>File-based, lightweight database with broad platform support.
     * Uses native libraries for optimal performance.</p>
     *
     * <p>JDBC URL format: {@code jdbc:sqlite:path/to/database.db}</p>
     */
    SQLITE("sqlite", "jdbc:sqlite:"),

    /**
     * H2 database engine.
     *
     * <p>Pure Java database with support for both file-based and in-memory modes.
     * Excellent for development, testing, and applications requiring advanced SQL features.</p>
     *
     * <p>JDBC URL formats:</p>
     * <ul>
     *   <li>File mode: {@code jdbc:h2:file:./path/to/database}</li>
     *   <li>In-memory: {@code jdbc:h2:mem:database_name}</li>
     * </ul>
     */
    H2("h2", "jdbc:h2:file:");

    private final String name;
    private final String jdbcPrefix;

    DatabaseType(String name, String jdbcPrefix) {
        this.name = name;
        this.jdbcPrefix = jdbcPrefix;
    }

    /**
     * Gets the database type name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the JDBC URL prefix for this database type.
     */
    public String getJdbcPrefix() {
        return jdbcPrefix;
    }

    /**
     * Builds a JDBC URL for file-based storage.
     *
     * @param filePath the path to the database file (without extension)
     * @return the complete JDBC URL
     */
    public String buildJdbcUrl(String filePath) {
        return jdbcPrefix + filePath;
    }

    /**
     * Builds a JDBC URL for H2 in-memory database.
     *
     * @param databaseName the name of the in-memory database
     * @return the complete JDBC URL
     * @throws UnsupportedOperationException if called on SQLite
     */
    public String buildInMemoryUrl(String databaseName) {
        if (this == SQLITE) {
            throw new UnsupportedOperationException("SQLite does not support in-memory mode via standard JDBC URL");
        }
        return "jdbc:h2:mem:" + databaseName;
    }

    /**
     * Detects the database type from a JDBC URL.
     *
     * @param jdbcUrl the JDBC URL
     * @return the detected database type
     * @throws IllegalArgumentException if the URL doesn't match a supported database
     */
    public static DatabaseType fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL cannot be null");
        }

        for (DatabaseType type : values()) {
            if (jdbcUrl.startsWith(type.jdbcPrefix) ||
                (type == H2 && jdbcUrl.startsWith("jdbc:h2:mem:"))) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported JDBC URL: " + jdbcUrl);
    }

    /**
     * Returns the default database type.
     */
    public static DatabaseType getDefault() {
        return SQLITE;
    }
}

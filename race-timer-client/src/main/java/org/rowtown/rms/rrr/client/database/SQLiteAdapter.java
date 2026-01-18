package org.rowtown.rms.rrr.client.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database adapter for SQLite.
 *
 * <p>SQLite is a lightweight, file-based database engine with excellent portability
 * and a small footprint. It's ideal for embedded systems, mobile applications,
 * and scenarios where simplicity is key.</p>
 *
 * <p><b>Characteristics:</b></p>
 * <ul>
 *   <li>Single file per database</li>
 *   <li>No separate server process</li>
 *   <li>ACID-compliant transactions</li>
 *   <li>Cross-platform compatibility</li>
 *   <li>Minimal configuration required</li>
 * </ul>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *   <li>No native BOOLEAN type (uses INTEGER 0/1)</li>
 *   <li>Limited concurrency (single writer at a time)</li>
 *   <li>No stored procedures</li>
 *   <li>TEXT type for all string data</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe.</p>
 */
@Slf4j
public class SQLiteAdapter implements DatabaseAdapter {

    @Override
    public DatabaseType getType() {
        return DatabaseType.SQLITE;
    }

    @Override
    public Connection connect(String databasePath) throws SQLException {
        String jdbcUrl = DatabaseType.SQLITE.buildJdbcUrl(databasePath);
        log.debug("Connecting to SQLite database: {}", jdbcUrl);

        Connection connection = DriverManager.getConnection(jdbcUrl);

        // Enable foreign keys (disabled by default in SQLite)
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        log.info("Connected to SQLite database: {}", databasePath);
        return connection;
    }

    @Override
    public Connection connectInMemory(String databaseName) throws SQLException {
        // SQLite supports in-memory databases via special syntax
        String jdbcUrl = "jdbc:sqlite::memory:";
        log.debug("Connecting to SQLite in-memory database");

        Connection connection = DriverManager.getConnection(jdbcUrl);

        // Enable foreign keys
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        log.info("Connected to SQLite in-memory database");
        return connection;
    }

    @Override
    public String getAutoIncrementSyntax() {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    public String getCurrentTimestampSyntax() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String getTextType(int maxLength) {
        // SQLite uses TEXT for all string data
        return "TEXT";
    }

    @Override
    public String getBooleanType() {
        // SQLite doesn't have native BOOLEAN, uses INTEGER
        return "INTEGER";
    }

    @Override
    public String getTimestampType() {
        // SQLite stores timestamps as TEXT in ISO 8601 format
        return "TEXT";
    }

    @Override
    public String adaptSql(String sql) {
        if (sql == null) {
            return null;
        }

        // Replace H2-specific syntax with SQLite equivalents
        return sql
            .replace(" AUTO_INCREMENT", " AUTOINCREMENT")
            .replace(" BOOLEAN ", " INTEGER ")
            .replace("BOOLEAN DEFAULT", "INTEGER DEFAULT")
            .replace("VARCHAR(", "TEXT") // Remove VARCHAR length specifications
            .replaceAll("VARCHAR\\([0-9]+\\)", "TEXT");
    }

    @Override
    public boolean supportsFeature(DatabaseFeature feature) {
        return switch (feature) {
            case IN_MEMORY -> true;
            case NATIVE_BOOLEAN -> false;
            case WINDOW_FUNCTIONS -> true; // SQLite 3.25.0+
            case CTE -> true; // Common Table Expressions supported
            case JSON_FUNCTIONS -> true; // SQLite 3.38.0+
            case FULL_TEXT_SEARCH -> true; // FTS5 extension
            case STORED_PROCEDURES -> false;
            case TRIGGERS -> true;
        };
    }

    /**
     * Configures SQLite-specific performance optimizations.
     *
     * @param connection the database connection
     * @throws SQLException if configuration fails
     */
    public void configurePerformance(Connection connection) throws SQLException {
        try (var stmt = connection.createStatement()) {
            // Use Write-Ahead Logging for better concurrency
            stmt.execute("PRAGMA journal_mode = WAL");

            // Increase cache size (in pages, negative = KB)
            stmt.execute("PRAGMA cache_size = -64000"); // 64MB

            // Synchronous mode: NORMAL is good balance of safety/performance
            stmt.execute("PRAGMA synchronous = NORMAL");

            // Use memory for temporary storage
            stmt.execute("PRAGMA temp_store = MEMORY");

            log.info("Applied SQLite performance optimizations");
        }
    }

    /**
     * Gets database statistics.
     *
     * @param connection the database connection
     * @return formatted statistics string
     * @throws SQLException if query fails
     */
    public String getStatistics(Connection connection) throws SQLException {
        StringBuilder stats = new StringBuilder("SQLite Statistics:\n");

        try (var stmt = connection.createStatement()) {
            // Page count and size
            var rs = stmt.executeQuery("PRAGMA page_count");
            if (rs.next()) {
                int pageCount = rs.getInt(1);
                stats.append("  Pages: ").append(pageCount).append("\n");
            }

            rs = stmt.executeQuery("PRAGMA page_size");
            if (rs.next()) {
                int pageSize = rs.getInt(1);
                stats.append("  Page Size: ").append(pageSize).append(" bytes\n");
            }

            rs = stmt.executeQuery("PRAGMA freelist_count");
            if (rs.next()) {
                int freePages = rs.getInt(1);
                stats.append("  Free Pages: ").append(freePages).append("\n");
            }

            rs = stmt.executeQuery("PRAGMA journal_mode");
            if (rs.next()) {
                String journalMode = rs.getString(1);
                stats.append("  Journal Mode: ").append(journalMode).append("\n");
            }
        }

        return stats.toString();
    }
}

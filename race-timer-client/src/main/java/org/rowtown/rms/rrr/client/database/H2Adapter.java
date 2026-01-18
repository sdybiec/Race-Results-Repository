package org.rowtown.rms.rrr.client.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database adapter for H2 Database.
 *
 * <p>H2 is a pure Java database engine with excellent performance and advanced SQL features.
 * It supports both file-based and in-memory modes, making it ideal for development,
 * testing, and production deployments.</p>
 *
 * <p><b>Characteristics:</b></p>
 * <ul>
 *   <li>Pure Java (no native dependencies)</li>
 *   <li>Fast performance for both reads and writes</li>
 *   <li>In-memory mode for testing</li>
 *   <li>Advanced SQL features (CTEs, window functions, etc.)</li>
 *   <li>Multiple connection modes (embedded, server, mixed)</li>
 *   <li>Compatibility modes for other databases (PostgreSQL, MySQL, etc.)</li>
 * </ul>
 *
 * <p><b>Advantages over SQLite:</b></p>
 * <ul>
 *   <li>Better write concurrency</li>
 *   <li>Native BOOLEAN type</li>
 *   <li>Stored procedures and functions</li>
 *   <li>More SQL standard compliance</li>
 *   <li>Built-in full-text search</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe.</p>
 */
@Slf4j
public class H2Adapter implements DatabaseAdapter {

    @Override
    public DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public Connection connect(String databasePath) throws SQLException {
        String jdbcUrl = DatabaseType.H2.buildJdbcUrl(databasePath);
        log.debug("Connecting to H2 database: {}", jdbcUrl);

        // H2 connection with recommended settings
        Connection connection = DriverManager.getConnection(jdbcUrl);

        // Configure H2 settings
        configureDatabaseSettings(connection);

        log.info("Connected to H2 database: {}", databasePath);
        return connection;
    }

    @Override
    public Connection connectInMemory(String databaseName) throws SQLException {
        String jdbcUrl = DatabaseType.H2.buildInMemoryUrl(databaseName);
        log.debug("Connecting to H2 in-memory database: {}", databaseName);

        Connection connection = DriverManager.getConnection(jdbcUrl);

        // Configure H2 settings
        configureDatabaseSettings(connection);

        log.info("Connected to H2 in-memory database: {}", databaseName);
        return connection;
    }

    @Override
    public String getAutoIncrementSyntax() {
        return "INTEGER PRIMARY KEY AUTO_INCREMENT";
    }

    @Override
    public String getCurrentTimestampSyntax() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String getTextType(int maxLength) {
        if (maxLength > 0) {
            return "VARCHAR(" + maxLength + ")";
        }
        return "VARCHAR"; // Unlimited length
    }

    @Override
    public String getBooleanType() {
        return "BOOLEAN";
    }

    @Override
    public String getTimestampType() {
        return "TIMESTAMP";
    }

    @Override
    public String adaptSql(String sql) {
        if (sql == null) {
            return null;
        }

        // Replace SQLite-specific syntax with H2 equivalents
        return sql
            .replace(" AUTOINCREMENT", " AUTO_INCREMENT")
            .replace("INTEGER DEFAULT 0", "BOOLEAN DEFAULT FALSE")
            .replace("INTEGER DEFAULT 1", "BOOLEAN DEFAULT TRUE")
            .replace(" TEXT ", " VARCHAR ")
            .replace(" TEXT,", " VARCHAR,")
            .replace("(TEXT)", "(VARCHAR)");
    }

    @Override
    public boolean supportsFeature(DatabaseFeature feature) {
        return switch (feature) {
            case IN_MEMORY -> true;
            case NATIVE_BOOLEAN -> true;
            case WINDOW_FUNCTIONS -> true;
            case CTE -> true;
            case JSON_FUNCTIONS -> true; // H2 2.0+
            case FULL_TEXT_SEARCH -> true;
            case STORED_PROCEDURES -> true;
            case TRIGGERS -> true;
        };
    }

    /**
     * Configures H2-specific database settings for optimal performance.
     *
     * @param connection the database connection
     * @throws SQLException if configuration fails
     */
    private void configureDatabaseSettings(Connection connection) throws SQLException {
        try (var stmt = connection.createStatement()) {
            // Enable MVCC (Multi-Version Concurrency Control) for better concurrency
            // Note: In H2 2.0+, MVCC is always enabled
            stmt.execute("SET MODE REGULAR"); // Standard SQL mode

            // Set cache size (in KB)
            stmt.execute("SET CACHE_SIZE 65536"); // 64MB

            // Disable auto-commit for better transaction control
            // This is typically done by the application, but we set it here as a default
            connection.setAutoCommit(true);

            log.debug("Applied H2 database settings");
        } catch (SQLException e) {
            log.warn("Failed to configure some H2 settings (may be using older version): {}",
                     e.getMessage());
        }
    }

    /**
     * Creates a connection with MySQL compatibility mode.
     *
     * <p>Useful for applications migrating from MySQL or requiring MySQL-specific syntax.</p>
     *
     * @param databasePath the path to the database
     * @return a connection with MySQL compatibility
     * @throws SQLException if connection fails
     */
    public Connection connectWithMySQLMode(String databasePath) throws SQLException {
        String jdbcUrl = DatabaseType.H2.buildJdbcUrl(databasePath) + ";MODE=MySQL";
        log.debug("Connecting to H2 database with MySQL mode: {}", jdbcUrl);

        Connection connection = DriverManager.getConnection(jdbcUrl);
        log.info("Connected to H2 database in MySQL compatibility mode");
        return connection;
    }

    /**
     * Creates a connection with PostgreSQL compatibility mode.
     *
     * <p>Useful for applications migrating from PostgreSQL or requiring PostgreSQL-specific syntax.</p>
     *
     * @param databasePath the path to the database
     * @return a connection with PostgreSQL compatibility
     * @throws SQLException if connection fails
     */
    public Connection connectWithPostgreSQLMode(String databasePath) throws SQLException {
        String jdbcUrl = DatabaseType.H2.buildJdbcUrl(databasePath) + ";MODE=PostgreSQL";
        log.debug("Connecting to H2 database with PostgreSQL mode: {}", jdbcUrl);

        Connection connection = DriverManager.getConnection(jdbcUrl);
        log.info("Connected to H2 database in PostgreSQL compatibility mode");
        return connection;
    }

    /**
     * Gets database statistics and information.
     *
     * @param connection the database connection
     * @return formatted statistics string
     * @throws SQLException if query fails
     */
    public String getStatistics(Connection connection) throws SQLException {
        StringBuilder stats = new StringBuilder("H2 Database Statistics:\n");

        try (var stmt = connection.createStatement()) {
            // Get database info
            var rs = stmt.executeQuery("SELECT * FROM INFORMATION_SCHEMA.SETTINGS");
            while (rs.next()) {
                String setting = rs.getString("SETTING_NAME");
                String value = rs.getString("SETTING_VALUE");

                // Only show relevant settings
                if (setting.contains("CACHE") || setting.contains("MODE") ||
                    setting.contains("VERSION") || setting.contains("LOCK")) {
                    stats.append("  ").append(setting).append(": ").append(value).append("\n");
                }
            }
        }

        return stats.toString();
    }

    /**
     * Enables H2 Console for debugging (embedded web-based admin interface).
     *
     * <p><b>WARNING:</b> Only use this in development environments. The H2 Console
     * can be a security risk if exposed in production.</p>
     *
     * @param port the port to run the console on (typically 8082)
     * @throws SQLException if console cannot be started
     */
    public void startConsole(int port) throws SQLException {
        try {
            // Start H2 web console
            org.h2.tools.Server server = org.h2.tools.Server.createWebServer(
                "-webPort", String.valueOf(port),
                "-webAllowOthers"
            );
            server.start();

            log.info("H2 Console started on port {}. Access at http://localhost:{}", port, port);
            log.warn("H2 Console should only be used in development environments");
        } catch (SQLException e) {
            log.error("Failed to start H2 Console", e);
            throw e;
        }
    }
}

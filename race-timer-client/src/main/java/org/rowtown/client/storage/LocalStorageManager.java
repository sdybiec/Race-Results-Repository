package org.rowtown.client.storage;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.client.model.LocalDocument;
import org.rowtown.client.model.SyncStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages local SQLite storage for offline-first operation.
 */
@Slf4j
public class LocalStorageManager implements AutoCloseable {

    private final String databasePath;
    private Connection connection;

    public LocalStorageManager(String databasePath) {
        this.databasePath = databasePath;
        initialize();
    }

    /**
     * Initialize database connection and create schema.
     */
    private void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            createSchema();
            log.info("Initialized local storage at: {}", databasePath);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize local storage", e);
        }
    }

    /**
     * Create database schema.
     */
    private void createSchema() throws SQLException {
        String createDocumentsTable = """
            CREATE TABLE IF NOT EXISTS documents (
                local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id INTEGER,
                regatta_id TEXT NOT NULL,
                timer_id TEXT,
                milestone_id TEXT,
                document_type TEXT NOT NULL,
                version_type TEXT,
                author TEXT NOT NULL,
                description TEXT,
                created_at TEXT,
                modified_at TEXT,
                local_version INTEGER DEFAULT 1,
                server_version INTEGER,
                sync_status TEXT NOT NULL DEFAULT 'PENDING',
                last_synced_at TEXT,
                last_sync_error TEXT,
                retry_count INTEGER DEFAULT 0,
                model_data BLOB,
                serialization_format TEXT DEFAULT 'JSON',
                local_created_at TEXT NOT NULL
            )
            """;

        String createPendingOperationsTable = """
            CREATE TABLE IF NOT EXISTS pending_operations (
                operation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id INTEGER,
                operation_type TEXT NOT NULL,
                operation_data TEXT,
                created_at TEXT NOT NULL,
                retry_count INTEGER DEFAULT 0,
                last_error TEXT,
                FOREIGN KEY (document_id) REFERENCES documents(local_id) ON DELETE CASCADE
            )
            """;

        String createSyncLogTable = """
            CREATE TABLE IF NOT EXISTS sync_log (
                log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_type TEXT NOT NULL,
                status TEXT NOT NULL,
                message TEXT,
                documents_synced INTEGER DEFAULT 0,
                started_at TEXT NOT NULL,
                completed_at TEXT
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDocumentsTable);
            stmt.execute(createPendingOperationsTable);
            stmt.execute(createSyncLogTable);

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_regatta ON documents(regatta_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_status ON documents(sync_status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_server_id ON documents(server_id)");
        }
    }

    /**
     * Save or update a document.
     */
    public LocalDocument save(LocalDocument doc) {
        if (doc.getLocalId() == null) {
            return insert(doc);
        } else {
            return update(doc);
        }
    }

    /**
     * Insert a new document.
     */
    private LocalDocument insert(LocalDocument doc) {
        String sql = """
            INSERT INTO documents (
                server_id, regatta_id, timer_id, milestone_id, document_type, version_type,
                author, description, created_at, modified_at, local_version, server_version,
                sync_status, last_synced_at, model_data, serialization_format, local_created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            pstmt.setObject(idx++, doc.getServerId());
            pstmt.setString(idx++, doc.getRegattaId());
            pstmt.setString(idx++, doc.getTimerId());
            pstmt.setString(idx++, doc.getMilestoneId());
            pstmt.setString(idx++, doc.getDocumentType());
            pstmt.setString(idx++, doc.getVersionType());
            pstmt.setString(idx++, doc.getAuthor());
            pstmt.setString(idx++, doc.getDescription());
            pstmt.setString(idx++, toString(doc.getCreatedAt()));
            pstmt.setString(idx++, toString(doc.getModifiedAt()));
            pstmt.setLong(idx++, doc.getLocalVersion() != null ? doc.getLocalVersion() : 1L);
            pstmt.setObject(idx++, doc.getServerVersion());
            pstmt.setString(idx++, doc.getSyncStatus().name());
            pstmt.setString(idx++, toString(doc.getLastSyncedAt()));
            pstmt.setBytes(idx++, doc.getModelData());
            pstmt.setString(idx++, doc.getSerializationFormat());
            pstmt.setString(idx++, toString(LocalDateTime.now()));

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    doc.setLocalId(rs.getLong(1));
                }
            }

            log.debug("Inserted document with local_id: {}", doc.getLocalId());
            return doc;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert document", e);
        }
    }

    /**
     * Update an existing document.
     */
    private LocalDocument update(LocalDocument doc) {
        String sql = """
            UPDATE documents SET
                server_id = ?, regatta_id = ?, timer_id = ?, milestone_id = ?,
                document_type = ?, version_type = ?, author = ?, description = ?,
                created_at = ?, modified_at = ?, local_version = ?, server_version = ?,
                sync_status = ?, last_synced_at = ?, last_sync_error = ?, retry_count = ?,
                model_data = ?, serialization_format = ?
            WHERE local_id = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            int idx = 1;
            pstmt.setObject(idx++, doc.getServerId());
            pstmt.setString(idx++, doc.getRegattaId());
            pstmt.setString(idx++, doc.getTimerId());
            pstmt.setString(idx++, doc.getMilestoneId());
            pstmt.setString(idx++, doc.getDocumentType());
            pstmt.setString(idx++, doc.getVersionType());
            pstmt.setString(idx++, doc.getAuthor());
            pstmt.setString(idx++, doc.getDescription());
            pstmt.setString(idx++, toString(doc.getCreatedAt()));
            pstmt.setString(idx++, toString(doc.getModifiedAt()));
            pstmt.setLong(idx++, doc.getLocalVersion());
            pstmt.setObject(idx++, doc.getServerVersion());
            pstmt.setString(idx++, doc.getSyncStatus().name());
            pstmt.setString(idx++, toString(doc.getLastSyncedAt()));
            pstmt.setString(idx++, doc.getLastSyncError());
            pstmt.setInt(idx++, doc.getRetryCount() != null ? doc.getRetryCount() : 0);
            pstmt.setBytes(idx++, doc.getModelData());
            pstmt.setString(idx++, doc.getSerializationFormat());
            pstmt.setLong(idx++, doc.getLocalId());

            pstmt.executeUpdate();
            log.debug("Updated document with local_id: {}", doc.getLocalId());
            return doc;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update document", e);
        }
    }

    /**
     * Find document by local ID.
     */
    public Optional<LocalDocument> findById(Long localId) {
        String sql = "SELECT * FROM documents WHERE local_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, localId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find document by id", e);
        }

        return Optional.empty();
    }

    /**
     * Find document by server ID.
     */
    public Optional<LocalDocument> findByServerId(Long serverId) {
        String sql = "SELECT * FROM documents WHERE server_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, serverId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find document by server id", e);
        }

        return Optional.empty();
    }

    /**
     * Find all documents by regatta ID and type.
     */
    public List<LocalDocument> findByRegattaAndType(String regattaId, String documentType) {
        String sql = "SELECT * FROM documents WHERE regatta_id = ? AND document_type = ?";
        List<LocalDocument> documents = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, regattaId);
            pstmt.setString(2, documentType);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    documents.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find documents", e);
        }

        return documents;
    }

    /**
     * Find all documents with pending sync.
     */
    public List<LocalDocument> findPendingSync() {
        String sql = "SELECT * FROM documents WHERE sync_status IN ('PENDING', 'FAILED') ORDER BY local_created_at";
        List<LocalDocument> documents = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                documents.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending documents", e);
        }

        return documents;
    }

    /**
     * Delete document by local ID.
     */
    public void delete(Long localId) {
        String sql = "DELETE FROM documents WHERE local_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, localId);
            pstmt.executeUpdate();
            log.debug("Deleted document with local_id: {}", localId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /**
     * Map ResultSet to LocalDocument.
     */
    private LocalDocument mapResultSet(ResultSet rs) throws SQLException {
        return LocalDocument.builder()
            .localId(rs.getLong("local_id"))
            .serverId(getLongOrNull(rs, "server_id"))
            .regattaId(rs.getString("regatta_id"))
            .timerId(rs.getString("timer_id"))
            .milestoneId(rs.getString("milestone_id"))
            .documentType(rs.getString("document_type"))
            .versionType(rs.getString("version_type"))
            .author(rs.getString("author"))
            .description(rs.getString("description"))
            .createdAt(toLocalDateTime(rs.getString("created_at")))
            .modifiedAt(toLocalDateTime(rs.getString("modified_at")))
            .localVersion(rs.getLong("local_version"))
            .serverVersion(getLongOrNull(rs, "server_version"))
            .syncStatus(SyncStatus.valueOf(rs.getString("sync_status")))
            .lastSyncedAt(toLocalDateTime(rs.getString("last_synced_at")))
            .lastSyncError(rs.getString("last_sync_error"))
            .retryCount(rs.getInt("retry_count"))
            .modelData(rs.getBytes("model_data"))
            .serializationFormat(rs.getString("serialization_format"))
            .localCreatedAt(toLocalDateTime(rs.getString("local_created_at")))
            .build();
    }

    private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String toString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    private LocalDateTime toLocalDateTime(String str) {
        return str != null ? LocalDateTime.parse(str) : null;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Closed local storage connection");
            } catch (SQLException e) {
                log.error("Error closing connection", e);
            }
        }
    }

    /**
     * Begin a transaction.
     */
    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    /**
     * Commit a transaction.
     */
    public void commitTransaction() throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }

    /**
     * Rollback a transaction.
     */
    public void rollbackTransaction() {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            log.error("Error rolling back transaction", e);
        }
    }
}

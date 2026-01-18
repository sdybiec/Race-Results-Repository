package org.rowtown.rms.rrr.client.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages a persistent queue of offline operations that need to be synchronized with the server.
 *
 * <p>Operations are stored in SQLite and survive application restarts. When connectivity
 * is restored, operations are replayed in the order they were queued.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All database operations are synchronized.
 *
 * <p><b>Database Schema:</b>
 * <pre>
 * CREATE TABLE pending_operations (
 *   operation_id TEXT PRIMARY KEY,
 *   operation_type TEXT NOT NULL,
 *   document_id INTEGER,
 *   regatta_id TEXT,
 *   timer_id TEXT,
 *   performed_at TEXT NOT NULL,
 *   queued_at TEXT NOT NULL,
 *   status TEXT NOT NULL,
 *   attempt_count INTEGER DEFAULT 0,
 *   last_error TEXT,
 *   last_attempt_at TEXT,
 *   has_dependencies INTEGER DEFAULT 0,
 *   depends_on_operation_id TEXT,
 *   document_json TEXT,
 *   metadata TEXT
 * );
 * </pre>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * OfflineOperationQueue queue = new OfflineOperationQueue(dbPath);
 * queue.initialize();
 *
 * // Queue an offline update
 * PendingOperation op = PendingOperation.builder()
 *     .operationType(PendingOperation.OperationType.UPDATE)
 *     .document(modifiedDocument)
 *     .build();
 * queue.enqueue(op);
 *
 * // When connection is restored
 * List<PendingOperation> pending = queue.getPendingOperations();
 * for (PendingOperation operation : pending) {
 *     try {
 *         replayOperation(operation);
 *         queue.markSucceeded(operation.getOperationId());
 *     } catch (Exception e) {
 *         queue.markFailed(operation.getOperationId(), e.getMessage());
 *     }
 * }
 * }</pre>
 */
@Slf4j
public class OfflineOperationQueue {

    private final String dbPath;
    private final ObjectMapper objectMapper;
    private Connection connection;

    /**
     * Creates an offline operation queue with the specified database path.
     *
     * @param dbPath path to the SQLite database file
     */
    public OfflineOperationQueue(String dbPath) {
        this.dbPath = dbPath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Initializes the queue by creating the database table if it doesn't exist.
     *
     * @throws SQLException if database initialization fails
     */
    public synchronized void initialize() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }

        String createTableSql = """
            CREATE TABLE IF NOT EXISTS pending_operations (
                operation_id TEXT PRIMARY KEY,
                operation_type TEXT NOT NULL,
                document_id INTEGER,
                regatta_id TEXT,
                timer_id TEXT,
                performed_at TEXT NOT NULL,
                queued_at TEXT NOT NULL,
                status TEXT NOT NULL,
                attempt_count INTEGER DEFAULT 0,
                last_error TEXT,
                last_attempt_at TEXT,
                has_dependencies INTEGER DEFAULT 0,
                depends_on_operation_id TEXT,
                document_json TEXT,
                metadata TEXT
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
            log.info("Initialized offline operation queue at {}", dbPath);
        }
    }

    /**
     * Adds an operation to the queue.
     *
     * @param operation the operation to queue
     * @throws SQLException if database operation fails
     */
    public synchronized void enqueue(PendingOperation operation) throws SQLException {
        if (operation.getOperationId() == null) {
            operation.setOperationId(UUID.randomUUID().toString());
        }
        if (operation.getQueuedAt() == null) {
            operation.setQueuedAt(LocalDateTime.now());
        }
        if (operation.getStatus() == null) {
            operation.setStatus(PendingOperation.OperationStatus.PENDING);
        }

        String sql = """
            INSERT INTO pending_operations (
                operation_id, operation_type, document_id, regatta_id, timer_id,
                performed_at, queued_at, status, attempt_count, last_error,
                last_attempt_at, has_dependencies, depends_on_operation_id,
                document_json, metadata
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, operation.getOperationId());
            pstmt.setString(2, operation.getOperationType().name());
            pstmt.setObject(3, operation.getDocumentId());
            pstmt.setString(4, operation.getRegattaId());
            pstmt.setString(5, operation.getTimerId());
            pstmt.setString(6, operation.getPerformedAt().toString());
            pstmt.setString(7, operation.getQueuedAt().toString());
            pstmt.setString(8, operation.getStatus().name());
            pstmt.setInt(9, operation.getAttemptCount());
            pstmt.setString(10, operation.getLastError());
            pstmt.setObject(11, operation.getLastAttemptAt() != null ? operation.getLastAttemptAt().toString() : null);
            pstmt.setInt(12, operation.isHasDependencies() ? 1 : 0);
            pstmt.setString(13, operation.getDependsOnOperationId());
            pstmt.setString(14, serializeDocument(operation.getDocument()));
            pstmt.setString(15, operation.getMetadata());

            pstmt.executeUpdate();
            log.debug("Queued operation {} of type {}", operation.getOperationId(), operation.getOperationType());
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize document", e);
        }
    }

    /**
     * Retrieves all pending operations that are ready to replay.
     *
     * @return list of operations with status PENDING and no dependencies
     * @throws SQLException if database operation fails
     */
    public synchronized List<PendingOperation> getPendingOperations() throws SQLException {
        String sql = """
            SELECT * FROM pending_operations
            WHERE status = 'PENDING' AND has_dependencies = 0
            ORDER BY queued_at ASC
            """;

        List<PendingOperation> operations = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                operations.add(mapResultSetToOperation(rs));
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize operation", e);
        }

        log.debug("Retrieved {} pending operations", operations.size());
        return operations;
    }

    /**
     * Retrieves all operations with FAILED status that can be retried.
     *
     * @return list of failed operations with attempt count < 5
     * @throws SQLException if database operation fails
     */
    public synchronized List<PendingOperation> getFailedOperations() throws SQLException {
        String sql = """
            SELECT * FROM pending_operations
            WHERE status = 'FAILED' AND attempt_count < 5
            ORDER BY queued_at ASC
            """;

        List<PendingOperation> operations = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                operations.add(mapResultSetToOperation(rs));
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize operation", e);
        }

        return operations;
    }

    /**
     * Retrieves all operations with CONFLICT status.
     *
     * @return list of operations that need conflict resolution
     * @throws SQLException if database operation fails
     */
    public synchronized List<PendingOperation> getConflictedOperations() throws SQLException {
        String sql = """
            SELECT * FROM pending_operations
            WHERE status = 'CONFLICT'
            ORDER BY queued_at ASC
            """;

        List<PendingOperation> operations = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                operations.add(mapResultSetToOperation(rs));
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize operation", e);
        }

        return operations;
    }

    /**
     * Marks an operation as currently replaying.
     *
     * @param operationId the operation ID
     * @throws SQLException if database operation fails
     */
    public synchronized void markReplaying(String operationId) throws SQLException {
        updateOperationStatus(operationId, PendingOperation.OperationStatus.REPLAYING);
    }

    /**
     * Marks an operation as successfully completed.
     *
     * @param operationId the operation ID
     * @throws SQLException if database operation fails
     */
    public synchronized void markSucceeded(String operationId) throws SQLException {
        updateOperationStatus(operationId, PendingOperation.OperationStatus.SUCCEEDED);
        log.info("Operation {} succeeded", operationId);
    }

    /**
     * Marks an operation as failed and records the error.
     *
     * @param operationId the operation ID
     * @param error the error message
     * @throws SQLException if database operation fails
     */
    public synchronized void markFailed(String operationId, String error) throws SQLException {
        String sql = """
            UPDATE pending_operations
            SET status = ?, last_error = ?, last_attempt_at = ?, attempt_count = attempt_count + 1
            WHERE operation_id = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, PendingOperation.OperationStatus.FAILED.name());
            pstmt.setString(2, error);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setString(4, operationId);
            pstmt.executeUpdate();
        }

        log.warn("Operation {} failed: {}", operationId, error);
    }

    /**
     * Marks an operation as conflicted.
     *
     * @param operationId the operation ID
     * @param conflictDetails details about the conflict
     * @throws SQLException if database operation fails
     */
    public synchronized void markConflicted(String operationId, String conflictDetails) throws SQLException {
        String sql = """
            UPDATE pending_operations
            SET status = ?, last_error = ?, last_attempt_at = ?
            WHERE operation_id = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, PendingOperation.OperationStatus.CONFLICT.name());
            pstmt.setString(2, conflictDetails);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setString(4, operationId);
            pstmt.executeUpdate();
        }

        log.warn("Operation {} conflicted: {}", operationId, conflictDetails);
    }

    /**
     * Removes all completed operations from the queue.
     *
     * @return number of operations removed
     * @throws SQLException if database operation fails
     */
    public synchronized int purgeCompletedOperations() throws SQLException {
        String sql = "DELETE FROM pending_operations WHERE status = 'SUCCEEDED'";
        try (Statement stmt = connection.createStatement()) {
            int count = stmt.executeUpdate(sql);
            log.info("Purged {} completed operations", count);
            return count;
        }
    }

    /**
     * Gets the total number of operations in the queue.
     *
     * @return total operation count
     * @throws SQLException if database operation fails
     */
    public synchronized int getQueueSize() throws SQLException {
        String sql = "SELECT COUNT(*) FROM pending_operations WHERE status != 'SUCCEEDED'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Closes the database connection.
     */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("Closed offline operation queue");
            }
        } catch (SQLException e) {
            log.error("Error closing database connection", e);
        }
    }

    private void updateOperationStatus(String operationId, PendingOperation.OperationStatus status) throws SQLException {
        String sql = "UPDATE pending_operations SET status = ?, last_attempt_at = ? WHERE operation_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, LocalDateTime.now().toString());
            pstmt.setString(3, operationId);
            pstmt.executeUpdate();
        }
    }

    private PendingOperation mapResultSetToOperation(ResultSet rs) throws SQLException, JsonProcessingException {
        return PendingOperation.builder()
                .operationId(rs.getString("operation_id"))
                .operationType(PendingOperation.OperationType.valueOf(rs.getString("operation_type")))
                .documentId(rs.getObject("document_id", Long.class))
                .regattaId(rs.getString("regatta_id"))
                .timerId(rs.getString("timer_id"))
                .performedAt(LocalDateTime.parse(rs.getString("performed_at")))
                .queuedAt(LocalDateTime.parse(rs.getString("queued_at")))
                .status(PendingOperation.OperationStatus.valueOf(rs.getString("status")))
                .attemptCount(rs.getInt("attempt_count"))
                .lastError(rs.getString("last_error"))
                .lastAttemptAt(rs.getString("last_attempt_at") != null ?
                        LocalDateTime.parse(rs.getString("last_attempt_at")) : null)
                .hasDependencies(rs.getInt("has_dependencies") == 1)
                .dependsOnOperationId(rs.getString("depends_on_operation_id"))
                .document(deserializeDocument(rs.getString("document_json")))
                .metadata(rs.getString("metadata"))
                .build();
    }

    private String serializeDocument(LocalDocument document) throws JsonProcessingException {
        return document != null ? objectMapper.writeValueAsString(document) : null;
    }

    private LocalDocument deserializeDocument(String json) throws JsonProcessingException {
        return json != null ? objectMapper.readValue(json, LocalDocument.class) : null;
    }
}

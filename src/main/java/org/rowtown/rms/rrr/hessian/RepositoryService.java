package org.rowtown.rms.rrr.hessian;

import org.rowtown.rms.rrr.dto.*;

import java.util.List;

/**
 * Hessian RPC service interface for the Race Results Repository.
 * Provides all operations available via REST API in a Java-friendly RPC format.
 */
public interface RepositoryService {

    // Document operations
    DocumentResponse createDocument(DocumentRequest request);
    DocumentResponse getDocument(Long documentId, Long version);
    DocumentResponse updateDocument(Long documentId, byte[] modelData, String changeDescription);
    void deleteDocument(Long documentId);

    // Version control
    List<VersionInfo> listVersions(Long documentId, int page, int size);
    VersionInfo getVersion(Long documentId, Long versionNumber);
    ModelDiff compareVersions(Long documentId, Long v1, Long v2);
    VersionInfo rollback(Long documentId, Long targetVersion, String description);

    // Search
    SearchResults search(SearchQuery query);

    // Webhook subscriptions
    Long subscribeWebhook(WebhookSubscriptionRequest request);
    void unsubscribeWebhook(Long subscriptionId);
    List<WebhookSubscriptionInfo> listWebhooks();

    // MQTT info
    MqttConnectionInfo getMqttInfo();
}

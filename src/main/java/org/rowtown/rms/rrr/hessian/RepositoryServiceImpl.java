package org.rowtown.rms.rrr.hessian;

import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.dto.*;
import org.rowtown.rms.rrr.service.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of the Hessian RPC service interface.
 */
@Service("hessianRepositoryService")
@RequiredArgsConstructor
public class RepositoryServiceImpl implements RepositoryService {

    private final DocumentManagerService documentManager;
    private final VersionControlService versionControl;
    private final ModelComparisonService comparisonService;
    private final SearchService searchService;
    private final WebhookService webhookService;
    private final MqttPublishingService mqttService;
    private final AuthorizationService authorizationService;

    @Override
    public DocumentResponse createDocument(DocumentRequest request) {
        return documentManager.createDocument(request);
    }

    @Override
    public DocumentResponse getDocument(Long documentId, Long version) {
        if (version == null) {
            return documentManager.getLatestDocument(documentId);
        }
        return documentManager.getDocument(documentId, version);
    }

    @Override
    public DocumentResponse updateDocument(Long documentId, byte[] modelData, String changeDescription) {
        String author = authorizationService.getCurrentUser().getUserId();
        return documentManager.updateDocument(documentId, modelData, author, changeDescription, SerializationFormat.XMI);
    }

    @Override
    public void deleteDocument(Long documentId) {
        documentManager.deleteDocument(documentId);
    }

    @Override
    public List<VersionInfo> listVersions(Long documentId, int page, int size) {
        return versionControl.listVersions(documentId, page, size).getContent();
    }

    @Override
    public VersionInfo getVersion(Long documentId, Long versionNumber) {
        var version = versionControl.getVersion(documentId, versionNumber);
        return VersionInfo.builder()
            .versionId(version.getVersionId())
            .documentId(documentId)
            .versionNumber(versionNumber)
            .timestamp(version.getTimestamp())
            .author(version.getAuthor())
            .changeDescription(version.getChangeDescription())
            .checksum(version.getChecksum())
            .size((long) version.getModelSnapshot().length)
            .build();
    }

    @Override
    public ModelDiff compareVersions(Long documentId, Long v1, Long v2) {
        var version1 = versionControl.getVersion(documentId, v1);
        var version2 = versionControl.getVersion(documentId, v2);
        return comparisonService.compareModels(
            version1.getModelSnapshot(), version1.getSnapshotFormat(),
            version2.getModelSnapshot(), version2.getSnapshotFormat());
    }

    @Override
    public VersionInfo rollback(Long documentId, Long targetVersion, String description) {
        String author = authorizationService.getCurrentUser().getUserId();
        var version = versionControl.rollback(documentId, targetVersion, author, description);
        return VersionInfo.builder()
            .versionId(version.getVersionId())
            .documentId(documentId)
            .versionNumber(version.getVersionNumber())
            .timestamp(version.getTimestamp())
            .author(version.getAuthor())
            .changeDescription(version.getChangeDescription())
            .checksum(version.getChecksum())
            .build();
    }

    @Override
    public SearchResults search(SearchQuery query) {
        return searchService.search(query);
    }

    @Override
    public Long subscribeWebhook(WebhookSubscriptionRequest request) {
        WebhookSubscriptionInfo info = webhookService.subscribe(request);
        return info.getSubscriptionId();
    }

    @Override
    public void unsubscribeWebhook(Long subscriptionId) {
        webhookService.unsubscribe(subscriptionId);
    }

    @Override
    public List<WebhookSubscriptionInfo> listWebhooks() {
        return webhookService.listSubscriptions();
    }

    @Override
    public MqttConnectionInfo getMqttInfo() {
        return mqttService.getConnectionInfo();
    }
}

package org.rowtown.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.domain.DocumentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a versioned document in the repository.
 * A document contains EMF Timing Data Interchange models and associated metadata.
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_regatta", columnList = "regattaId"),
    @Index(name = "idx_timer", columnList = "timerId"),
    @Index(name = "idx_type", columnList = "documentType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "documentType", nullable = false)
    private DocumentType documentType;

    @Column(name = "regattaId", nullable = false)
    private String regattaId;

    @Column(name = "timerId")
    private String timerId;

    @Column(name = "milestoneId")
    private String milestoneId;

    @Column(name = "versionType")
    private String versionType;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "latest_version", nullable = false)
    private Long latestVersion;

    @Column(name = "description", length = 2000)
    private String description;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Version> versions = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentMetadata> metadata = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentTag> tags = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (latestVersion == null) {
            latestVersion = 0L;
        }
    }

    public void addVersion(Version version) {
        versions.add(version);
        version.setDocument(this);
        this.latestVersion = version.getVersionNumber();
    }

    public void addMetadata(DocumentMetadata meta) {
        metadata.add(meta);
        meta.setDocument(this);
    }

    public void addTag(DocumentTag tag) {
        tags.add(tag);
        tag.setDocument(this);
    }
}

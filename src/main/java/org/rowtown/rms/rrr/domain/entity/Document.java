package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.rowtown.rms.rrr.domain.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for versioned documents.
 *
 * <p>Uses single-table inheritance keyed on the {@code documentType}
 * discriminator, so all documents share one {@code documents} table (and the
 * existing version / metadata / tag / ownership relationships) while the two
 * concrete kinds — {@link StartListDocument} and {@link RaceResultsDocument} —
 * carry their own fields.</p>
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_documents_regatta", columnList = "regattaId"),
    @Index(name = "idx_documents_type", columnList = "documentType")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "documentType", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    // Read-only mapping over the discriminator column, so getDocumentType() keeps
    // working without a separately writable field.
    @Enumerated(EnumType.STRING)
    @Column(name = "documentType", insertable = false, updatable = false)
    private DocumentType documentType;

    @Column(name = "regattaId", nullable = false)
    private String regattaId;

    // Regattas are periodic (typically annual), so a regatta is identified by its
    // name (regattaId) together with its start date.
    @Column(name = "regatta_start_date", nullable = false)
    private LocalDate regattaStartDate;

    // The nsURI of the EMF metamodel this document's model data conforms to
    // (e.g. http://www.rowtown.org/TDI/1.0.0). Derived from the model, it makes
    // each document self-describing and carries the model version for migration.
    @Column(name = "model_ns_uri")
    private String modelNsUri;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "latest_version", nullable = false)
    private Long latestVersion;

    @Column(name = "description", length = 2000)
    private String description;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Version> versions = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentMetadata> metadata = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
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

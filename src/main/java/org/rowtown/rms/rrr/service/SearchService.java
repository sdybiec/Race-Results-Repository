package org.rowtown.rms.rrr.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.DocumentMetadata;
import org.rowtown.rms.rrr.domain.entity.DocumentTag;
import org.rowtown.rms.rrr.dto.DocumentSummary;
import org.rowtown.rms.rrr.dto.SearchQuery;
import org.rowtown.rms.rrr.dto.SearchResults;
import org.rowtown.rms.rrr.repository.DocumentMetadataRepository;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.rowtown.rms.rrr.repository.DocumentTagRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for searching documents with various criteria.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository metadataRepository;
    private final DocumentTagRepository tagRepository;

    /**
     * Search documents based on query criteria.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "search-results", unless = "#result == null")
    public SearchResults search(SearchQuery query) {
        // Build specification based on query
        Specification<Document> spec = buildSpecification(query);

        // Setup pagination
        int page = query.getPage() != null ? query.getPage() : 0;
        int size = query.getSize() != null ? query.getSize() : 10;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Execute search
        Page<Document> results = documentRepository.findAll(spec, pageable);

        // Filter by tags if specified
        List<Document> filteredDocuments = results.getContent();
        if (query.getTags() != null && !query.getTags().isEmpty()) {
            filteredDocuments = filterByTags(filteredDocuments, query.getTags());
        }

        // Filter by metadata if specified
        if (StringUtils.hasText(query.getMetadataKey())) {
            filteredDocuments = filterByMetadata(filteredDocuments, query.getMetadataKey(), query.getMetadataValue());
        }

        // Convert to summaries
        List<DocumentSummary> summaries = filteredDocuments.stream()
            .map(this::toDocumentSummary)
            .collect(Collectors.toList());

        return SearchResults.builder()
            .total(results.getTotalElements())
            .page(page)
            .pageSize(size)
            .results(summaries)
            .build();
    }

    /**
     * Build JPA Specification based on search query.
     */
    private Specification<Document> buildSpecification(SearchQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Document ID (exact match)
            if (StringUtils.hasText(query.getDocumentId())) {
                try {
                    Long docId = Long.parseLong(query.getDocumentId());
                    predicates.add(criteriaBuilder.equal(root.get("documentId"), docId));
                } catch (NumberFormatException e) {
                    log.warn("Invalid document ID: {}", query.getDocumentId());
                }
            }

            // Regatta ID
            if (StringUtils.hasText(query.getRegattaId())) {
                predicates.add(buildStringPredicate(criteriaBuilder, root.get("regattaId"),
                    query.getRegattaId(), query.getMatchType()));
            }

            // Regatta start date (exact match; distinguishes annual editions)
            if (query.getRegattaStartDate() != null) {
                predicates.add(criteriaBuilder.equal(root.get("regattaStartDate"), query.getRegattaStartDate()));
            }

            // Document Type
            if (query.getDocumentType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("documentType"), query.getDocumentType()));
            }

            // Timer ID
            if (StringUtils.hasText(query.getTimerId())) {
                predicates.add(buildStringPredicate(criteriaBuilder, root.get("timerId"),
                    query.getTimerId(), query.getMatchType()));
            }

            // Author
            if (StringUtils.hasText(query.getAuthor())) {
                predicates.add(buildStringPredicate(criteriaBuilder, root.get("author"),
                    query.getAuthor(), query.getMatchType()));
            }

            // Description (full-text search)
            if (StringUtils.hasText(query.getDescription())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")),
                    "%" + query.getDescription().toLowerCase() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Build predicate for string matching based on match type.
     */
    private Predicate buildStringPredicate(jakarta.persistence.criteria.CriteriaBuilder cb,
                                          jakarta.persistence.criteria.Expression<String> field,
                                          String value, SearchQuery.MatchType matchType) {
        if (matchType == null) {
            matchType = SearchQuery.MatchType.EXACT;
        }

        return switch (matchType) {
            case EXACT -> cb.equal(field, value);
            case PARTIAL -> cb.like(cb.lower(field), "%" + value.toLowerCase() + "%");
            case WILDCARD -> {
                String pattern = value.replace("*", "%").replace("?", "_");
                yield cb.like(cb.lower(field), pattern.toLowerCase());
            }
        };
    }

    /**
     * Filter documents by tags.
     */
    private List<Document> filterByTags(List<Document> documents, Set<String> requiredTags) {
        return documents.stream()
            .filter(doc -> {
                Set<String> docTags = tagRepository.findByDocument_DocumentId(doc.getDocumentId())
                    .stream()
                    .map(DocumentTag::getTagName)
                    .collect(Collectors.toSet());
                return docTags.containsAll(requiredTags);
            })
            .collect(Collectors.toList());
    }

    /**
     * Filter documents by metadata.
     */
    private List<Document> filterByMetadata(List<Document> documents, String key, String value) {
        return documents.stream()
            .filter(doc -> {
                List<DocumentMetadata> metadata = metadataRepository.findByDocument_DocumentId(doc.getDocumentId());
                return metadata.stream()
                    .anyMatch(meta -> meta.getKey().equals(key) &&
                        (value == null || meta.getValue().contains(value)));
            })
            .collect(Collectors.toList());
    }

    /**
     * Convert Document entity to DocumentSummary DTO.
     */
    private DocumentSummary toDocumentSummary(Document document) {
        // Fetch metadata
        Map<String, String> metadata = metadataRepository.findByDocument_DocumentId(document.getDocumentId())
            .stream()
            .collect(Collectors.toMap(DocumentMetadata::getKey, DocumentMetadata::getValue));

        // Fetch tags
        Set<String> tags = tagRepository.findByDocument_DocumentId(document.getDocumentId())
            .stream()
            .map(DocumentTag::getTagName)
            .collect(Collectors.toSet());

        return DocumentSummary.builder()
            .documentId(document.getDocumentId())
            .type(document.getDocumentType())
            .regattaId(document.getRegattaId())
            .regattaStartDate(document.getRegattaStartDate())
            .timerId(document.getTimerId())
            .latestVersion(document.getLatestVersion())
            .lastModified(document.getCreatedAt())
            .author(document.getAuthor())
            .description(document.getDescription())
            .tags(tags)
            .metadata(metadata)
            .build();
    }
}

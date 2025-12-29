package org.rowtown.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.domain.DocumentType;
import org.rowtown.domain.Operation;
import org.rowtown.domain.UserRole;
import org.rowtown.exception.AuthorizationException;
import org.rowtown.repository.AclEntryRepository;
import org.rowtown.repository.DocumentOwnershipRepository;
import org.rowtown.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for handling authorization checks based on ACLs and ownership.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final AclEntryRepository aclEntryRepository;
    private final DocumentOwnershipRepository ownershipRepository;

    /**
     * Check if the current user is authorized to perform an operation on a document type in a regatta.
     */
    public void checkAuthorization(String regattaId, DocumentType documentType, Operation operation) {
        UserPrincipal user = getCurrentUser();

        // Regatta admin has full access
        if (user.hasRole(UserRole.REGATTA_ADMIN) && user.hasAccessToRegatta(regattaId)) {
            return;
        }

        // Check ACL for user's role
        for (UserRole role : user.getRoles()) {
            // Check wildcard ACL first
            if (aclEntryRepository.isAllowed("*", documentType, operation, role)) {
                return;
            }

            // Check regatta-specific ACL
            if (aclEntryRepository.isAllowed(regattaId, documentType, operation, role)) {
                return;
            }
        }

        throw new AuthorizationException(
            String.format("User %s not authorized for operation %s on %s in regatta %s",
                user.getUserId(), operation, documentType, regattaId));
    }

    /**
     * Check if the current user owns a specific document.
     */
    public boolean isOwner(Long documentId) {
        UserPrincipal user = getCurrentUser();
        return ownershipRepository.existsByDocument_DocumentIdAndOwnerUserId(documentId, user.getUserId());
    }

    /**
     * Check if the current user is authorized to modify a specific document.
     * For Race Results: must be owner or regatta admin.
     * For Start List: must be regatta admin.
     */
    public void checkDocumentAccess(Long documentId, String regattaId, DocumentType documentType,
                                   Operation operation) {
        UserPrincipal user = getCurrentUser();

        // Regatta admin has full access
        if (user.hasRole(UserRole.REGATTA_ADMIN) && user.hasAccessToRegatta(regattaId)) {
            return;
        }

        // For UPDATE/DELETE/ROLLBACK operations on Race Results, check ownership
        if (documentType == DocumentType.RACE_RESULTS &&
            (operation == Operation.UPDATE || operation == Operation.DELETE || operation == Operation.ROLLBACK)) {
            if (isOwner(documentId)) {
                return;
            }
        }

        // Fall back to general ACL check
        checkAuthorization(regattaId, documentType, operation);
    }

    /**
     * Get the current authenticated user.
     */
    public UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthorizationException("No authenticated user found");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }

    /**
     * Check if current user has a specific role.
     */
    public boolean hasRole(UserRole role) {
        try {
            UserPrincipal user = getCurrentUser();
            return user.hasRole(role);
        } catch (AuthorizationException e) {
            return false;
        }
    }
}

package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Supplies the {@link ResourceSet} used to load and save EMF models.
 *
 * <p>This is the single seam through which model loading is customized. The
 * default implementation registers the generated model {@code EPackage}s and can
 * only load the current model version. To support loading (and auto-upgrading)
 * older stored versions, provide an alternative bean — e.g. one backed by an
 * {@code UpgradingResourceSet} — and it will override the default without any
 * change to {@link ModelSerializationService} or its callers.</p>
 */
public interface ModelResourceSetFactory {

    /**
     * @return a fresh {@link ResourceSet} configured for the repository's models.
     *         A new instance per call keeps callers thread-safe (EMF resources
     *         are not thread-safe).
     */
    ResourceSet newResourceSet();
}

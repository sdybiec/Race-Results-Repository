package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.util.List;

/**
 * Default {@link ModelResourceSetFactory}: a plain {@link ResourceSetImpl} with
 * an XMI resource factory and all generated model {@link EPackage}s registered
 * per-{@link ResourceSet} (rather than in the global
 * {@link EPackage.Registry#INSTANCE}).
 *
 * <p>This loads only the <em>current</em> model version; documents in an older
 * namespace will fail to resolve their {@code EPackage} and be rejected by
 * validation. Replace this bean with an upgrading factory to load older
 * versions.</p>
 */
public class DefaultModelResourceSetFactory implements ModelResourceSetFactory {

    private final List<EPackage> modelPackages;

    /**
     * @param modelPackages all model {@link EPackage} beans; an empty list means
     *                      only dynamic/core EMF models can be handled.
     */
    public DefaultModelResourceSetFactory(List<EPackage> modelPackages) {
        this.modelPackages = modelPackages;
    }

    @Override
    public ResourceSet newResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("*", new XMIResourceFactoryImpl());

        for (EPackage pkg : modelPackages) {
            resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);
        }
        return resourceSet;
    }
}

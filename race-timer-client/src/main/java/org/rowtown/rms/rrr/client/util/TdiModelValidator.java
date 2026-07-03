package org.rowtown.rms.rrr.client.util;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.rowtown.rms.tdi.TimingDataInterchangePackage;

import java.io.ByteArrayInputStream;

/**
 * Validates that a TDI model loads into the generated classes before it is saved
 * or uploaded.
 *
 * <p>Mirrors the server's fail-fast ingest policy: a model the generated TDI
 * classes cannot load is unusable elsewhere in the timing system, so the client
 * rejects it at capture time rather than queueing it for a sync that the server
 * would reject.</p>
 */
public final class TdiModelValidator {

    /** Namespace URI of the TDI model. */
    public static final String TDI_NS_URI = TimingDataInterchangePackage.eNS_URI;

    private TdiModelValidator() {
    }

    /**
     * Verify that the given bytes load into the generated TDI classes.
     *
     * @param modelData XMI-serialized TDI model bytes
     * @throws IllegalArgumentException if the data cannot be loaded by the
     *         generated TDI classes or is not a TDI model
     */
    public static void validateLoadable(byte[] modelData) {
        if (modelData == null || modelData.length == 0) {
            throw new IllegalArgumentException("Model data is required");
        }

        EObject root;
        try {
            ResourceSet resourceSet = new ResourceSetImpl();
            resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
            resourceSet.getPackageRegistry()
                .put(TimingDataInterchangePackage.eNS_URI, TimingDataInterchangePackage.eINSTANCE);

            Resource resource = resourceSet.createResource(URI.createURI("capture.xmi"));
            resource.load(new ByteArrayInputStream(modelData), null);
            root = resource.getContents().isEmpty() ? null : resource.getContents().get(0);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Race results model cannot be loaded by the generated TDI classes "
                    + "and would be unusable in the timing system: " + e.getMessage(), e);
        }

        if (root == null) {
            throw new IllegalArgumentException("Model data did not contain a loadable TDI model object");
        }

        EPackage ePackage = root.eClass().getEPackage();
        String nsUri = ePackage != null ? ePackage.getNsURI() : null;
        if (!TDI_NS_URI.equals(nsUri)) {
            throw new IllegalArgumentException(
                "Model is not a TDI model (expected namespace " + TDI_NS_URI + " but was " + nsUri + ")");
        }
    }
}

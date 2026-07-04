package org.rowtown.rms.rrr.client.util;

import com.humanfactor.rw.model.regatta.RegattaPackage;
import com.humanfactor.rw.model.reportdesigner.ReportDesignerPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.ByteArrayInputStream;

/**
 * Validates that an RML model loads into the generated classes before it is saved
 * or uploaded, mirroring the server's fail-fast ingest policy.
 *
 * <p>RML depends on the ReportDesign model, so both {@link EPackage}s are
 * registered. This is the single client-side place that references the generated
 * {@code rml-model} classes.</p>
 */
public final class RmlModelValidator {

    /** Namespace URI of the RML model. */
    public static final String RML_NS_URI = RegattaPackage.eNS_URI;

    private RmlModelValidator() {
    }

    /**
     * Verify that the given bytes load into the generated RML classes.
     *
     * @param modelData XMI-serialized RML model bytes
     * @throws IllegalArgumentException if the data cannot be loaded by the
     *         generated classes or is not an RML model
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
                .put(RegattaPackage.eNS_URI, RegattaPackage.eINSTANCE);
            resourceSet.getPackageRegistry()
                .put(ReportDesignerPackage.eNS_URI, ReportDesignerPackage.eINSTANCE);

            Resource resource = resourceSet.createResource(URI.createURI("regatta.rml"));
            resource.load(new ByteArrayInputStream(modelData), null);
            root = resource.getContents().isEmpty() ? null : resource.getContents().get(0);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Regatta definition cannot be loaded by the generated RML classes "
                    + "and would be unusable: " + e.getMessage(), e);
        }

        if (root == null) {
            throw new IllegalArgumentException("Model data did not contain a loadable RML model object");
        }

        EPackage ePackage = root.eClass().getEPackage();
        String nsUri = ePackage != null ? ePackage.getNsURI() : null;
        if (!RML_NS_URI.equals(nsUri)) {
            throw new IllegalArgumentException(
                "Model is not an RML model (expected namespace " + RML_NS_URI + " but was " + nsUri + ")");
        }
    }
}

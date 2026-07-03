package org.rowtown.rms.rrr.service;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Service for serializing and deserializing EMF models.
 *
 * <p>Any model {@link EPackage} beans in the context (e.g. the generated TDI
 * package from {@link org.rowtown.rms.rrr.config.TdiModelConfig}) are registered
 * per-{@link ResourceSet}. This lets XMI load into the generated (typed) classes
 * instead of failing on an unknown {@code nsURI}, and keeps registration local
 * rather than mutating the global {@link EPackage.Registry#INSTANCE}.</p>
 *
 * <p>A fresh {@link ResourceSet} is created per call, so the service is
 * thread-safe (EMF resources are not).</p>
 */
@Service
public class ModelSerializationService {

    private final List<EPackage> modelPackages;

    /**
     * @param modelPackages all model {@link EPackage} beans; Spring injects an
     *                      empty list when none are present, in which case only
     *                      dynamic/core EMF models can be handled.
     */
    public ModelSerializationService(List<EPackage> modelPackages) {
        this.modelPackages = modelPackages;
    }

    /**
     * Create a ResourceSet configured for XMI with all model packages registered.
     */
    private ResourceSet newResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("*", new XMIResourceFactoryImpl());

        for (EPackage pkg : modelPackages) {
            resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);
        }
        return resourceSet;
    }

    /**
     * Serialize an EMF model to bytes in the specified format.
     */
    public byte[] serialize(Object model, SerializationFormat format) throws IOException {
        ResourceSet resourceSet = newResourceSet();

        Resource resource = resourceSet.createResource(URI.createURI("temp." + format.name().toLowerCase()));
        resource.getContents().add((EObject) model);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.save(outputStream, null);
        return outputStream.toByteArray();
    }

    /**
     * Deserialize bytes to an EMF model.
     *
     * <p>When a matching model package is registered, the returned object is an
     * instance of the corresponding generated class (e.g. {@code TimingRegatta}).</p>
     */
    public Object deserialize(byte[] data, SerializationFormat format) throws IOException {
        ResourceSet resourceSet = newResourceSet();

        Resource resource = resourceSet.createResource(URI.createURI("temp." + format.name().toLowerCase()));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        resource.load(inputStream, null);

        if (resource.getContents().isEmpty()) {
            return null;
        }
        return resource.getContents().get(0);
    }

    /**
     * Deserialize bytes to the root {@link EObject}, or {@code null} if empty.
     */
    public EObject deserializeToEObject(byte[] data, SerializationFormat format) throws IOException {
        Object model = deserialize(data, format);
        return model instanceof EObject eObject ? eObject : null;
    }

    /**
     * Calculate SHA-256 checksum of model data.
     */
    public String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validate checksum of model data.
     */
    public boolean validateChecksum(byte[] data, String expectedChecksum) {
        String actualChecksum = calculateChecksum(data);
        return actualChecksum.equals(expectedChecksum);
    }
}

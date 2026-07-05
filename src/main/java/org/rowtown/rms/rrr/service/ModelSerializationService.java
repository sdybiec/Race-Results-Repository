package org.rowtown.rms.rrr.service;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service for serializing and deserializing EMF models.
 *
 * <p>The {@link ResourceSet} used for each operation is supplied by a
 * {@link ModelResourceSetFactory}, so how models are loaded (including support
 * for older versions via an upgrading {@code ResourceSet}) can be swapped without
 * touching this service. The default factory registers the generated model
 * {@link EPackage}s per-{@link ResourceSet}.</p>
 *
 * <p>A fresh {@link ResourceSet} is created per call, so the service is
 * thread-safe (EMF resources are not).</p>
 */
@Service
public class ModelSerializationService {

    private final ModelResourceSetFactory resourceSetFactory;

    public ModelSerializationService(ModelResourceSetFactory resourceSetFactory) {
        this.resourceSetFactory = resourceSetFactory;
    }

    private ResourceSet newResourceSet() {
        return resourceSetFactory.newResourceSet();
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

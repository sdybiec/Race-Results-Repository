package org.rowtown.rms.rrr.service;

import org.eclipse.emf.common.util.URI;
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
import java.util.HexFormat;

/**
 * Service for serializing and deserializing EMF models.
 */
@Service
public class ModelSerializationService {

    /**
     * Serialize an EMF model to bytes in the specified format.
     */
    public byte[] serialize(Object model, SerializationFormat format) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("*", new XMIResourceFactoryImpl());

        Resource resource = resourceSet.createResource(URI.createURI("temp." + format.name().toLowerCase()));
        resource.getContents().add((org.eclipse.emf.ecore.EObject) model);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.save(outputStream, null);
        return outputStream.toByteArray();
    }

    /**
     * Deserialize bytes to an EMF model.
     */
    public Object deserialize(byte[] data, SerializationFormat format) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry()
            .getExtensionToFactoryMap()
            .put("*", new XMIResourceFactoryImpl());

        Resource resource = resourceSet.createResource(URI.createURI("temp." + format.name().toLowerCase()));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        resource.load(inputStream, null);

        if (resource.getContents().isEmpty()) {
            return null;
        }
        return resource.getContents().get(0);
    }

    /**
     * Calculate SHA-256 checksum of model data.
     */
    public String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
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

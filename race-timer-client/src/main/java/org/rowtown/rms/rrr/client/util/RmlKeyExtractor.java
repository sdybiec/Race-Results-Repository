package org.rowtown.rms.rrr.client.util;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * Extracts the regatta key (name + start date) from an RML (Regatta Modeling
 * Language) document's root {@code Regatta} element.
 *
 * <p>Uses StAX so it does not depend on the generated model's datatype
 * converters. Mirrors the server's authoritative derivation so the client keys
 * its local RML record the same way.</p>
 */
public final class RmlKeyExtractor {

    private static final XMLInputFactory XML_INPUT_FACTORY = createSecureFactory();

    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }

    private RmlKeyExtractor() {
    }

    /**
     * @return the regatta name ({@code Regatta.name}), or {@code null} if absent
     */
    public static String extractRegattaName(byte[] modelData) {
        return rootAttribute(modelData, "name");
    }

    /**
     * @return the regatta start date ({@code Regatta.startDate}, ISO-8601), or
     *         {@code null} if absent
     */
    public static String extractRegattaStartDate(byte[] modelData) {
        return rootAttribute(modelData, "startDate");
    }

    private static String rootAttribute(byte[] modelData, String localName) {
        if (modelData == null || modelData.length == 0) {
            return null;
        }
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(modelData));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            if (localName.equals(reader.getAttributeLocalName(i))) {
                                String value = reader.getAttributeValue(i);
                                return value != null && !value.isBlank() ? value : null;
                            }
                        }
                        return null; // only inspect the root element
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            // Not parseable — caller decides how to handle.
        }
        return null;
    }
}

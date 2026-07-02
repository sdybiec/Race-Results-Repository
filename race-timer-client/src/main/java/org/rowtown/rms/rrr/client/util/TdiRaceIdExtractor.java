package org.rowtown.rms.rrr.client.util;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * Extracts the race identifier from an XMI-serialized TDI model.
 *
 * <p>Mirrors the server's derivation so the offline-first client can key/label
 * Race Results by race before syncing. The server is the authority; this is used
 * for local records.</p>
 */
public final class TdiRaceIdExtractor {

    private static final XMLInputFactory XML_INPUT_FACTORY = createSecureFactory();

    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }

    private TdiRaceIdExtractor() {
    }

    /**
     * @param modelData XMI model bytes
     * @return the first {@code raceId} attribute value, or {@code null} if absent/unparseable
     */
    public static String extractRaceId(byte[] modelData) {
        if (modelData == null || modelData.length == 0) {
            return null;
        }
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(modelData));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            if ("raceId".equals(reader.getAttributeLocalName(i))) {
                                String value = reader.getAttributeValue(i);
                                if (value != null && !value.isBlank()) {
                                    return value;
                                }
                            }
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            // Not parseable as XMI, or no raceId — caller decides how to handle.
        }
        return null;
    }
}

package org.rowtown.rms.rrr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * Schema-agnostic inspection of a serialized EMF (XMI) document's root element.
 *
 * <p>Reads just the root element via StAX, so it works for any EMF model without
 * depending on generated classes or their datatype converters — used to record
 * each document's metamodel {@code nsURI} and to derive keys from root
 * attributes (e.g. the RML {@code Regatta}'s {@code name}/{@code startDate}).</p>
 */
@Service
@Slf4j
public class EmfDocumentInspector {

    private static final XMLInputFactory XML_INPUT_FACTORY = createSecureFactory();

    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // Harden against XXE / entity-expansion in client-submitted models.
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }

    /**
     * @param modelData XMI model bytes
     * @return the namespace URI of the document's root element, or {@code null}
     *         if the data is empty / not well-formed XML
     */
    public String rootNamespace(byte[] modelData) {
        return atRoot(modelData, XMLStreamReader::getNamespaceURI);
    }

    /**
     * @param modelData XMI model bytes
     * @param localName the attribute's local name on the root element
     * @return the attribute value on the root element, or {@code null} if absent
     *         / unparseable
     */
    public String rootAttribute(byte[] modelData, String localName) {
        return atRoot(modelData, reader -> {
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                if (localName.equals(reader.getAttributeLocalName(i))) {
                    String value = reader.getAttributeValue(i);
                    return value != null && !value.isBlank() ? value : null;
                }
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface RootFunction {
        String apply(XMLStreamReader reader);
    }

    private String atRoot(byte[] modelData, RootFunction function) {
        if (modelData == null || modelData.length == 0) {
            return null;
        }
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(modelData));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return function.apply(reader);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            log.debug("Could not read root element of model data: {}", e.getMessage());
        }
        return null;
    }
}

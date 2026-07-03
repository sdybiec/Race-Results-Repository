package org.rowtown.rms.rrr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * Extracts identifying information from a submitted TDI (Timing Data Interchange)
 * model.
 *
 * <p>Race Results documents derive their {@code raceId} from the model rather
 * than accepting it from the client, so the stored key always agrees with the
 * document contents.</p>
 */
@Service
@Slf4j
public class TdiModelInspector {

    private static final XMLInputFactory XML_INPUT_FACTORY = createSecureFactory();

    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // Harden against XXE / entity-expansion in client-submitted models.
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }

    /**
     * Extracts the race identifier from an XMI-serialized TDI model, returning the
     * first {@code raceId} attribute encountered (e.g. on a {@code timingRace}
     * element).
     *
     * @param modelData the XMI model bytes
     * @return the raceId, or {@code null} if the model has none / is unparseable
     */
    public String extractRaceId(byte[] modelData) {
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
            log.debug("Could not parse model as XMI to extract raceId: {}", e.getMessage());
        }
        return null;
    }
}

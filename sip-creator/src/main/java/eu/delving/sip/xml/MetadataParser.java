/*
 * Copyright 2007 EDL FOUNDATION
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.groovy.GroovyNode;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.MetadataRecordFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.ProgressListener;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Iterate through the xml file, producing groovy nodes.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class MetadataParser {
    private InputStream inputStream;
    private XMLStreamReader2 input;
    private Path recordRoot;
    private int recordIndex, recordCount;
    private Path path = new Path();
    private Map<String, String> namespaces = new TreeMap<String, String>();
    private MetadataRecordFactory factory = new MetadataRecordFactory(namespaces);
    private ProgressListener progressListener;

    public MetadataParser(InputStream inputStream, Path recordRoot, int recordCount) throws XMLStreamException {
        this.inputStream = inputStream;
        this.recordRoot = recordRoot;
        this.recordCount = recordCount;
        XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
        xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        xmlif.configureForSpeed();
        this.input = (XMLStreamReader2) xmlif.createXMLStreamReader("Metadata", inputStream);
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        if (progressListener != null) {
            progressListener.prepareFor(recordCount);
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized MetadataRecord nextRecord() throws XMLStreamException, IOException, AbortException {
        MetadataRecord metadataRecord = null;
        GroovyNode node = null;
        StringBuilder value = new StringBuilder();
        while (metadataRecord == null) {
            switch (input.getEventType()) {
                case XMLEvent.START_DOCUMENT:
                    break;
                case XMLEvent.START_ELEMENT:
                    path.push(Tag.create(input.getName().getPrefix(), input.getName().getLocalPart()));
                    if (node == null && path.equals(recordRoot)) {
                        node = new GroovyNode(null, "input");
                    }
                    else if (node != null) {
                        node = new GroovyNode(node, input.getNamespaceURI(), input.getLocalName(), input.getPrefix());
                        if (!input.getPrefix().isEmpty()) {
                            namespaces.put(input.getPrefix(), input.getNamespaceURI());
                        }
                        if (input.getAttributeCount() > 0) {
                            for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                                QName attributeName = input.getAttributeName(walk);
                                node.attributes().put(attributeName.getLocalPart(), input.getAttributeValue(walk));
                            }
                        }
                        value.setLength(0);
                    }
                    break;
                case XMLEvent.CHARACTERS:
                case XMLEvent.CDATA:
                    if (node != null) {
                        value.append(input.getText());
                    }
                    break;
                case XMLEvent.END_ELEMENT:
                    if (node != null) {
                        String valueString = ValueFilter.filter(value.toString());
                        value.setLength(0);
                        if (!valueString.isEmpty()) {
                            // todo: perhaps check if there is already an array of children in there.
                            node.setValue(valueString);
                        }
                        if (path.equals(recordRoot)) {
                            if (node.parent() != null) {
                                throw new RuntimeException("Expected to be at root node");
                            }
                            metadataRecord = factory.fromGroovyNode(node, recordIndex++, recordCount);
                            if (progressListener != null) {
                                if (!progressListener.setProgress(recordIndex)) {
                                    throw new AbortException();
                                }
                            }
                            node = null;
                        }
                        else {
                            node = node.parent();
                        }
                    }
                    path.pop();
                    break;
                case XMLEvent.END_DOCUMENT: {
                    break;
                }
            }
            if (!input.hasNext()) {
                inputStream.close();
                if (progressListener != null) {
                    progressListener.finished(true);
                }
                break;
            }
            input.next();
        }
        return metadataRecord;
    }

    public void close() {
        try {
            input.close();
        }
        catch (XMLStreamException e) {
            e.printStackTrace(); // should never happen
        }
    }

    public static class AbortException extends Exception {
        public AbortException() {
            super("Metadata parsing aborted");
        }
    }
}
/*
 * Copyright 2011, 2012 Delving BV
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

import eu.delving.MappingResult;
import eu.delving.XMLToolFactory;
import eu.delving.metadata.RecDef;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gather the link checks from valid records coming from the MappingResultImpl
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkCheckExtractor {
    private Map<String, XPathExpression> expressionMap = new HashMap<String, XPathExpression>();
    private List<RecDef.FieldMarker> fieldMarkers;

    public LinkCheckExtractor(List<RecDef.FieldMarker> fieldMarkers, NamespaceContext pathContext) throws XPathExpressionException {
        this.fieldMarkers = fieldMarkers;
        for (RecDef.FieldMarker fieldMarker : fieldMarkers) {
            if (fieldMarker.check == null || !fieldMarker.hasPath()) continue;
            expressionMap.put(fieldMarker.getXPath(), XMLToolFactory.xpath(pathContext).compile(fieldMarker.getXPath()));
        }
    }

    public List<String> getChecks(MappingResult mappingResult) throws XPathExpressionException {
        List<String> checks = new ArrayList<String>();
        for (RecDef.FieldMarker fieldMarker : fieldMarkers) {
            if (fieldMarker.check == null || !fieldMarker.hasPath()) continue;
            XPathExpression expression = expressionMap.get(fieldMarker.getXPath());
            NodeList nodeList = (NodeList) expression.evaluate(mappingResult.root(), XPathConstants.NODESET);
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                checks.add(String.format("<<<%s>>>%s", fieldMarker.check, node.getTextContent().trim()));
            }
        }
        return checks;
    }
}

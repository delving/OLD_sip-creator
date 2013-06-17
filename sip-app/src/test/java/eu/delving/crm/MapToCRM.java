/*
 * Copyright 2013 Delving BV
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

package eu.delving.crm;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.XppDriver;
import eu.delving.XMLToolFactory;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MapToCRM {
    private static Logger LOG = Logger.getLogger(MapToCRM.class);
    private static final XPathFactory PATH_FACTORY = XMLToolFactory.xpathFactory();
    private static final NamespaceContext NAMESPACE_CONTEXT = new XPathContext(new String[][]{
            {"lido", "http://www.lido-schema.org"}
    });

    public static Mappings readMappings(InputStream inputStream) throws IOException, ParserConfigurationException, SAXException {
        return (Mappings) stream().fromXML(inputStream);
    }

    public static String toString(Mappings mappings) {
        return stream().toXML(mappings);
    }

    private static XStream stream() {
        XStream xstream = new XStream(new PureJavaReflectionProvider(), new XppDriver(new NoNameCoder()));
        xstream.setMode(XStream.NO_REFERENCES);
        xstream.processAnnotations(Mappings.class);
        return xstream;
    }

    private static XPath path() {
        XPath path = PATH_FACTORY.newXPath();
        path.setNamespaceContext(NAMESPACE_CONTEXT);
        return path;
    }

    @XStreamAlias("mappings")
    public static class Mappings {
        @XStreamAsAttribute
        public String version;

        @XStreamAsAttribute
        public String uriPolicyClass;

        @XStreamImplicit
        public List<Mapping> mappings;

        public Graph toGraph(Element element, URIGenerator generator) {
            Graph graph = new Graph();
            for (Mapping mapping : mappings) {
                mapping.apply(graph, element, generator);
            }
            return graph;
        }
    }

    @XStreamAlias("mapping")
    public static class Mapping {
        public Domain domain;

        @XStreamImplicit
        public List<Link> links;

        public void apply(Graph graph, Element element, URIGenerator generator) {
            domain.entity.resolveAt(element, null, generator);
            LOG.info("Domain: " + domain.entity);
            for (Link link : links) {
                link.apply(graph, element, domain, generator);
            }
        }
    }

    @XStreamAlias("domain")
    public static class Domain {
        public String source;

        public Entity entity;
    }

    @XStreamAlias("entity")
    public static class Entity {
        @XStreamAsAttribute
        public CRMEntity tag;

        @XStreamAsAttribute
        public String binding;

        @XStreamAlias("exists")
        public Exists exists;

        @XStreamAlias("uri_function")
        public URIFunction uriFunction;

        @XStreamOmitField
        public String uri;

        public boolean resolveAt(Element element, String subjectUri, URIGenerator generator) {
            if (exists != null && !exists.resolveAt(element)) return false;
            if (uriFunction != null) {
                uri = uriFunction.executeAt(element, this, subjectUri, generator);
            }
            else {
                // todo: what if there's no function?
            }
            return true;
        }

        public String toString() {
            return String.format("Entity(%s):[%s]", tag, uri);
        }
    }

    @XStreamAlias("property")
    public static class Property {
        @XStreamAsAttribute
        public CRMProperty tag;

        @XStreamAlias("exists")
        public Exists exists;

        public boolean resolveAt(Element element) {
            return exists == null || exists.resolveAt(element);
        }

        public String toString() {
            return String.format("Property(%s)", tag);
        }
    }

    @XStreamAlias("link")
    public static class Link {
        public Path path;

        public Range range;

        public void apply(Graph graph, Element element, Domain domain, URIGenerator generator) {
            path.apply(graph, element, domain, generator);
            range.apply(graph, element, path, domain, generator);
        }
    }

    @XStreamAlias("range")
    public static class Range {
        public String source;

        public Entity entity;

        @XStreamAlias("additional_node")
        public AdditionalNode additionalNode;

        public void apply(Graph graph, Element element, Path path, Domain domain, URIGenerator generator) {
            // todo: stick source on top of domain source, for a new element, not this one
            entity.resolveAt(element, domain.entity.uri, generator);
            LOG.info("Range: " + entity);
            if (entity.uri == null) return;
            // todo: domain-path-range is a triple!
            if (additionalNode != null) {
                additionalNode.apply(graph, element, entity, domain.entity.uri, generator);
            }
        }
    }

    @XStreamAlias("path")
    public static class Path {
        public String source;

        public Property property;

        @XStreamAlias("internal_node")
        public InternalNode internalNode;

        public void apply(Graph graph, Element element, Domain domain, URIGenerator generator) {
            if (!property.resolveAt(element)) return;
            LOG.info("Path: " + property);
            if (internalNode != null) {
                internalNode.apply(graph, element, domain, property, generator);
            }
            // todo: implement
        }
    }

    @XStreamAlias("additional_node")
    public static class AdditionalNode {
        public Property property;
        public Entity entity;

        public void apply(Graph graph, Element element, Entity rangeEntity, String domainURI, URIGenerator generator) {
            entity.resolveAt(element, domainURI, generator);
            if (entity.uri == null) return;
            //todo: rangeEntity-property-entity is a triple!
        }
    }

    @XStreamAlias("internal_node")
    public static class InternalNode {
        public Entity entity;
        public Property property;

        public void apply(Graph graph, Element element, Domain domain, Property pathProperty, URIGenerator generator) {
            entity.resolveAt(element, domain.entity.uri, generator);
            LOG.info("InternalNode: " + entity);
            if (!property.resolveAt(element)) return;
            // todo: domain-pathProperty-entity is a triple!
        }
    }

    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"xpath"})
    @XStreamAlias("exists")
    public static class Exists {
        @XStreamAsAttribute
        public String value;

        public String xpath;

        public boolean resolveAt(Element element) {
            // todo: evaluate the xpath
            // todo: compare if value != null
            return false;
        }
    }

    @XStreamAlias("uri_function")
    public static class URIFunction {
        @XStreamAsAttribute
        public String name;

        @XStreamImplicit
        public List<Arg> args;

        @XStreamOmitField
        public Map<String, String> argMap = new TreeMap<String, String>();

        public String executeAt(Element element, Entity entity, String domainURI, URIGenerator generator) {
            Set<String> argNames = generator.getArgNames(name);
            switch (argNames.size()) {
                case 0:
                    throw new RuntimeException(String.format("Lack of args in %s call to %s", entity.tag, name));
                case 1:
                    String singleArg = argNames.iterator().next();
                    if (args == null) {
                        Arg arg = new Arg();
                        arg.name = singleArg;
                        arg.content = "text()";
                        argMap.put(singleArg, arg.resolveAt(element));
                    }
                    else if (args.size() == 1) {
                        argMap.put(singleArg, args.get(0).resolveAt(element));
                    }
                    else {
                        throw new RuntimeException("Arg mismatch");
                    }
                    break;
                default:
                    if (args != null) {
                        for (Arg arg : args) {
                            if (arg.name == null) throw new RuntimeException("Arg lacks name");
                            if (!argNames.contains(arg.name)) throw new RuntimeException("Arg not found: " + arg.name);
                            argMap.put(arg.name, arg.resolveAt(element));
                        }
                    }
                    break;
            }
            return generator.createURI(entity, domainURI, name, argMap);
        }
    }

    @XStreamAlias("arg")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class Arg {
        @XStreamAsAttribute
        public String name;

        public String content;

        public String toString() {
            return content;
        }

        public String resolveAt(Element element) {
            return valueAt(element, content);
        }
    }

    public interface URIGenerator {
        Set<String> getArgNames(String name);

        String createURI(Entity entity, String domainURI, String name, Map<String, String> argMap);
    }

    // =======================================================================
    private static String valueAt(Node context, String expressionString) {
        List<Node> nodes = nodeList(context, expressionString);
        if (nodes.isEmpty()) return "";
        String value = nodes.get(0).getNodeValue();
        if (value == null) return "";
        return value.trim();
    }

    private static List<Node> nodeList(Node context, String expressionString) {
        try {
            XPathExpression expression = path().compile(expressionString);
            NodeList nodeList = (NodeList) expression.evaluate(context, XPathConstants.NODESET);
            List<Node> list = new ArrayList<Node>(nodeList.getLength());
            for (int index = 0; index < nodeList.getLength(); index++) list.add(nodeList.item(index));
            return list;
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("XPath Problem", e);
        }
    }

    private static class XPathContext implements NamespaceContext {
        private Map<String, String> prefixUri = new TreeMap<String, String>();
        private Map<String, String> uriPrefix = new TreeMap<String, String>();

        public XPathContext(String[][] prefixUriStrings) {
            for (String[] pair : prefixUriStrings) {
                prefixUri.put(pair[0], pair[1]);
                uriPrefix.put(pair[1], pair[0]);
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefixUri.size() == 1) {
                return prefixUri.values().iterator().next();
            }
            return prefixUri.get(prefix);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return uriPrefix.get(namespaceURI);
        }

        @Override
        public Iterator getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) return null;
            List<String> list = new ArrayList<String>();
            list.add(prefix);
            return list.iterator();
        }
    }
}

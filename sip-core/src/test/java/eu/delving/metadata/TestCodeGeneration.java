/*
 * Copyright 2011 DELVING BV
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

package eu.delving.metadata;

import com.thoughtworks.xstream.XStream;
import eu.delving.groovy.*;
import groovy.util.Node;
import junit.framework.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.*;

/**
 * Make sure the right code is being generated
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestCodeGeneration {

    static final RecMapping recMapping = RecMapping.create("lido", recDefModel());

    static {
        recMapping.getRecDefTree().setListener(new RecDefNode.Listener() {
            @Override
            public void nodeMappingSet(RecDefNode recDefNode) {
                System.out.println("Mapping set: " + recDefNode);
            }
        });
    }

    @Test
    public void cornucopia() throws MappingException {
        recMapping.setFact("dogExists", "true");

        node("/lidoWrap/lido/@sortorder").setNodeMapping(mapping("/leadup/@orderofsort"));
        node("/lidoWrap/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet").setNodeMapping(mapping("/leadup/record/list/member"));
        node("/lidoWrap/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/@sortorder").setNodeMapping(mapping("/leadup/record/list/member/@index"));

        RecDefNode termNode = node("/lidoWrap/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectConcept/term");
        NodeMapping term = termNode.setNodeMapping(mapping("/leadup/record/list/member/concept"));
        term.dictionary = new TreeMap<String, String>();
        term.dictionary.put("delving", "rulez");

        RecDefNode actorNode = node("/lidoWrap/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectActor/displayActor");
        actorNode.setNodeMapping(mapping("/leadup/record/list/member/name"));

        String code = recMapping.toCode(null, null);
        System.out.println(code);

        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping);
        Node node = mappingRunner.runMapping(createInputRecord());
        StringWriter writer = new StringWriter();
        XmlNodePrinter printer = new XmlNodePrinter(writer);
        printer.print(node);
        System.out.println(writer.toString());
    }

    private MetadataRecord createInputRecord() {
        GroovyNode input = new GroovyNode(null, "input");
        GroovyNode leadup = new GroovyNode(input, "leadup");
        new GroovyNode(leadup, "@orderofsort", "backward");
        GroovyNode record = new GroovyNode(leadup, "record");
        GroovyNode list = new GroovyNode(record, "list");
        GroovyNode member1 = new GroovyNode(list, "member");
        new GroovyNode(member1, "@index", "23");
        new GroovyNode(member1, "name", "Gumby");
        new GroovyNode(member1, "concept", "superhero");
        GroovyNode member2 = new GroovyNode(list, "member");
        new GroovyNode(member2, "@index", "45");
        new GroovyNode(member2, "name", "Pokey");
        new GroovyNode(member2, "concept", "sidekick");
        Map<String,String> ns = new TreeMap<String, String>();
        ns.put("lido", "http://lidoland");
        return new MetadataRecordFactory(ns).fromGroovyNode(input, -1, 1);
    }

    private static NodeMapping mapping(String path) {
        return new NodeMapping().setInputPath(Path.create(path));
    }

    private static RecDefNode node(String path) {
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(Path.create(path).defaultPrefix("lido"));
        Assert.assertNotNull(node);
        return node;
    }

    private static RecDefModel recDefModel() {
        return new RecDefModel() {
            @Override
            public List<FactDefinition> getFactDefinitions() {
                try {
                    InputStream inputStream = getClass().getResource("/facts-definition-list.xml").openStream();
                    XStream stream = new XStream();
                    stream.processAnnotations(FactDefinition.class);
                    Reader reader = new InputStreamReader(inputStream, "UTF-8");
                    FactDefinition.List factDefinitions = (FactDefinition.List) stream.fromXML(reader);
                    return factDefinitions.factDefinitions;
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load fact definitions", e);
                }
            }

            @Override
            public Set<String> getPrefixes() throws MetadataException {
                Set<String> prefixes = new TreeSet<String>();
                prefixes.add("lido");
                return prefixes;
            }

            @Override
            public RecDefTree createRecDef(String prefix) {
                if (!"lido".equals(prefix)) throw new RuntimeException();
                try {
                    InputStream inputStream = getClass().getResource("/lido-recdef.xml").openStream();
                    return RecDefTree.create(RecDef.read(inputStream));
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load recdef", e);
                }
            }
        };
    }
}

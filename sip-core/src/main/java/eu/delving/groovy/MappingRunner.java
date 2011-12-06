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

package eu.delving.groovy;

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import groovy.lang.*;
import groovy.util.Node;
import groovy.xml.NamespaceBuilder;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This core class takes a RecMapping and execute the code that it can generate to
 * transform an input to output in the form of a tree of Groovy Nodes using the
 * normal Groovy NodeBuilder class.
 *
 * It wraps the mapping code in the MappingCategory for DSL features, and before
 * executing the mapping it binds the input and output to the script to be run.
 *
 * There is a special case in which a specific selected path of the record definition
 * is being compiled, potentially even with edited code.  In this case the whole builder
 * is not created, but only the necessary code to render the given path.  This is for
 * showing results while the user is adjusting the code of a single snippet, and only
 * gives that part of the record output.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingRunner {
    private Script script;
    private GroovyShell groovyShell;
    private RecMapping recMapping;
    private String code;
    private int counter = 0;

    public MappingRunner(GroovyCodeResource groovyCodeResource, RecMapping recMapping, Path selectedPath, String editedCode) {
        this.groovyShell = groovyCodeResource.getCategoryShell();
        this.recMapping = recMapping;
        this.code = recMapping.getRecDefTree().toCode(recMapping.getFacts(), selectedPath, editedCode);
        script = groovyCodeResource.createMappingScript(code);
    }

    public MappingRunner(GroovyCodeResource groovyCodeResource, RecMapping recMapping) {
        this(groovyCodeResource, recMapping, null, null);
    }

    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    public Node runMapping(MetadataRecord metadataRecord) throws MappingException, DiscardRecordException {

        // Groovy generates classes for each script evaluation
        // this ends up eating up all permGen space
        // thus we clear the caches referencing those classes so that GC can remove them

        // additionally Groovy also at each script evaluation generates instances of MetaMethodIndex$Elem
        // those are SoftReferences so they only disappear when the used memory reaches its max allowed heap
        // but they also pretty much impact on the execution time, probably because method cache lookup time increases
        // (maybe because of a poorly implemented equals() & hashcode() implementation)
        // thus in order to get rid of this performance impact we need a reasonabily low -XX:MaxPermSize
        // yet it can't be too low because otherwise Groovy won't be able to generate its classes anymore
        // this is why we now clear those every 50 iterations.

        GroovySystem.setKeepJavaMetaClasses(false);
        if((counter % 50) == 0) {

            for(Iterator it = GroovySystem.getMetaClassRegistry().iterator(); it.hasNext();) {
                it.remove();
            }

            this.groovyShell.resetLoadedClasses();
            this.groovyShell.getClassLoader().clearCache();
        }
        if (metadataRecord == null) {
            throw new RuntimeException("Null input metadata record");
        }
        counter += 1;
//        long now = System.currentTimeMillis();
        try {
            Binding binding = new Binding();
            NodeBuilder builder = NodeBuilder.newInstance();
            NamespaceBuilder xmlns = new NamespaceBuilder(builder);
            binding.setVariable("output", builder);
            for (RecDef.Namespace ns : recMapping.getRecDefTree().getNamespaces()) {
                binding.setVariable(ns.prefix, xmlns.namespace(ns.uri, ns.prefix));
            }
            binding.setVariable("input", metadataRecord.getRootNode());
            script.setBinding(binding);
            return (Node) script.run();
        }
        catch (DiscardRecordException e) {
            throw e;
        }
        catch (MissingPropertyException e) {
            throw new MappingException(metadataRecord, "Missing Property " + e.getProperty(), e);
        }
        catch (MultipleCompilationErrorsException e) {
            StringBuilder out = new StringBuilder();
            for (Object o : e.getErrorCollector().getErrors()) {
                SyntaxErrorMessage message = (SyntaxErrorMessage) o;
                @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) SyntaxException se = message.getCause();
                // line numbers will not match
                out.append(String.format("Problem: %s\n", se.getOriginalMessage()));
            }
            throw new MappingException(metadataRecord, out.toString(), e);
        }
        catch (Exception e) {
            String codeLines = fetchCodeLines(e);
            if (codeLines != null) {
                throw new MappingException(metadataRecord, "Script Exception:\n"+codeLines, e);
            }
            else {
                throw new MappingException(metadataRecord, "Unexpected: " + e.toString(), e);
            }
        }
//        finally {
//            System.out.println("Mapping time: "+ (System.currentTimeMillis() - now));
//        }
    }

    // a dirty hack which parses the exception's stack trace.  any better strategy welcome, but it works.
    private String fetchCodeLines(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        e.printStackTrace(out);
        String trace = sw.toString();
        Pattern pattern = Pattern.compile("Script1.groovy:([0-9]*)");
        Matcher matcher = pattern.matcher(trace);
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder();
            int lineNumber = Integer.parseInt(matcher.group(1));
            for (String line : code.split("\n")) {
                lineNumber--;
                if (Math.abs(lineNumber) <= 2) {
                    sb.append(lineNumber == 0 ? ">>>" : "   ");
                    sb.append(line).append('\n');
                }
            }
            sb.append("----------- What happened ------------\n");
            sb.append(e.toString());
            return sb.toString();
        }
        return null;
    }
}

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

package eu.delving.metadata;

import eu.delving.groovy.StandardMappingFunctions;

import java.util.*;

import static eu.delving.metadata.OptRole.CHILD;
import static eu.delving.metadata.OptRole.ROOT;
import static eu.delving.metadata.StringUtil.*;

public class CodeGenerator {
    public static final String ABSENT_IS_FALSE = "_absent_ = false";
    private RecMapping recMapping;
    private EditPath editPath;
    private CodeOut codeOut = new CodeOut();
    private String prefixFirstBuilder = "outputNode = WORLD.output.";
    private boolean trace;

    public CodeGenerator(RecMapping recMapping) {
        this.recMapping = recMapping;
    }

    public CodeGenerator withEditPath(EditPath editPath) {
        this.editPath = editPath;
        return this;
    }

    public CodeGenerator withTrace(boolean trace) {
        this.trace = trace;
        return this;
    }

    public String toRecordMappingCode() {
        generate();
        return codeOut.toString();
    }

    public String toNodeMappingCode() {
        codeOut.setNodeMapping(editPath.getNodeMapping());
        generate();
        return codeOut.getNodeMappingCode();
    }

    private void generate() {
        codeOut.line("// SIP-Creator Generated Mapping Code");
        codeOut.line("// ----------------------------------");
        codeOut.line("// Discarding:");
        codeOut.line("import eu.delving.groovy.DiscardRecordException");
        codeOut.line("import eu.delving.metadata.OptList");
        codeOut.line("def discard = { reason -> throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("Object _facts = WORLD._facts");
        codeOut.line("Object _optLookup = WORLD._optLookup");
        for (Map.Entry<String, String> entry : recMapping.getFacts().entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                value = value.replace("'", "\'");
            }
            codeOut.line(String.format("String %s = '''%s'''", entry.getKey(), value));
        }
        codeOut.line("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'");

        StandardMappingFunctions.appendStandardFunctionsToScript(codeOut);

        codeOut.line("// Functions from Mapping:");
        Set<String> names = new TreeSet<>();
        for (MappingFunction function : recMapping.getFunctions()) {
            function.toCode(codeOut);
            names.add(function.name);
        }
        RecDefTree recDefTree = recMapping.getRecDefTree();
        RecDef recDef = recDefTree.getRecDef();
        if (recDef.functions != null) {
            codeOut.line("// Functions from Record Definition:");
            for (MappingFunction function : recDef.functions) {
                if (names.contains(function.name)) continue;
                function.toCode(codeOut);
                names.add(function.name);
            }
        }
        codeOut.line("// Dictionaries:");
        recDefTree.getNodeMappings().forEach(this::toLookupCode);
        codeOut.line("// DSL Category wraps Builder call:");
        codeOut.line("boolean _absent_ = true");
        codeOut.line("def outputNode");
        codeOut.line_("use (MappingCategory) {");
        codeOut.line_("WORLD.input * { _input ->");
        codeOut.line("_uniqueIdentifier = _input['@id'][0].toString()");
        if (recDefTree.getRoot().isPopulated()) {
            toElementCode(recDefTree.getRoot(), new Stack<String>());
        }
        else {
            codeOut.line("no 'mapping'");
        }
        codeOut._line("}");
        codeOut.line("outputNode");
        codeOut._line("}");
        codeOut.line("// ----------------------------------");
    }


    private void toElementCode(RecDefNode recDefNode, Stack<String> groovyParams) {
        if (recDefNode.isAttr() || !recDefNode.isPopulated()) {
            return;
        }
        if (editPath != null && !recDefNode.getPath().isFamilyOf(editPath.getNodeMapping().outputPath)) {
            return;
        }
        if (recDefNode.getNodeMappings().isEmpty()) {
            if (recDefNode.isRootOptNoOptList()) {
                Set<Path> siblingPaths = getSiblingInputPathsOfChildren(recDefNode);
                if (siblingPaths != null && !siblingPaths.isEmpty()) {
                    NodeMapping nodeMapping = new NodeMapping().setOutputPath(recDefNode.getPath()).setInputPaths(siblingPaths);
                    nodeMapping.recDefNode = recDefNode;
                    codeOut.start(nodeMapping);
                    toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
                    codeOut.end(nodeMapping);
                    return;
                }
            }
            if (!recDefNode.isLeafElem()) {
                toBranchCode(recDefNode, groovyParams);
            }
            else if (recDefNode.hasActiveAttributes()) {
                startBuilderCall(recDefNode, false, groovyParams);
                if (recDefNode.isChildOpt()) {
                    codeOut.line(recDefNode.getOptBox().getInnerOptReference());
                }
                else {
                    codeOut.line("// no node mappings");
                }
                codeOut._line("}");
            }
        }
        else if (editPath != null && editPath.getNodeMapping().recDefNode == recDefNode) {
            NodeMapping nodeMapping = editPath.getNodeMapping();
            codeOut.line("_absent_ = true");
            codeOut.start(nodeMapping);
            toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
            codeOut.end(nodeMapping);
            addIfAbsentCode(recDefNode, nodeMapping, groovyParams);
        }
        else {
            for (NodeMapping nodeMapping : recDefNode.getNodeMappings().values()) {
                codeOut.line("_absent_ = true");
                codeOut.start(nodeMapping);
                toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
                codeOut.end(nodeMapping);
                addIfAbsentCode(recDefNode, nodeMapping, groovyParams);
            }
        }
    }

    private void addIfAbsentCode(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        List<String> groovyCode = nodeMapping.groovyCode;
        if (editPath != null) {
            if (!editPath.isGeneratedCode()) {
                String editedCode = editPath.getEditedCode(recDefNode.getPath());
                if (editedCode != null) groovyCode = StringUtil.stringToLines(editedCode);
            }
        }
        List<String> ifAbsentCode = getIfAbsentCode(groovyCode);
        if (ifAbsentCode != null) {
            codeOut.line_("if (_absent_) {");
            startBuilderCall(recDefNode, false, groovyParams);
            indentCode(ifAbsentCode, codeOut);
            codeOut._line("}");
            codeOut._line("}");
        }
    }

    private void toNodeMappingLoop(RecDefNode recDefNode, NodeMapping nodeMapping, Path path, Stack<String> groovyParams) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            if (recDefNode.isLeafElem()) {
                if (recDefNode.getOptList() == null) {
                    toLeafCode(recDefNode, nodeMapping, groovyParams);
                }
                else {
                    boolean lookup = toLookupStatement(recDefNode, nodeMapping);
                    toLeafCode(recDefNode, nodeMapping, groovyParams);
                    if (lookup) codeOut._line("}");
                }
            }
            else {
                boolean lookup = nodeMapping.valueHasDictionary() && toLookupStatement(recDefNode, nodeMapping);
                toBranchCode(recDefNode, groovyParams);
                if (lookup) codeOut._line("}");
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            if (groovyParams.contains(getMapName(nodeMapping))) {
                toMapNodeMapping(recDefNode, nodeMapping, groovyParams);
            }
            else {
                trace();
                codeOut.line_(
                        "%s %s { %s ->",
                        toMapExpression(nodeMapping, path), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
                );
                groovyParams.push(getMapName(nodeMapping));
                toMapNodeMapping(recDefNode, nodeMapping, groovyParams);
                groovyParams.pop();
                codeOut._line("}");
            }
        }
        else { // path should never be empty
            Operator operator = nodeMapping.getOperator();
            if (path.size() > 2 && operator != Operator.FIRST) operator = Operator.ALL;
            String param = toLoopGroovyParam(path);
            if (groovyParams.contains(param)) {
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), groovyParams);
            }
            else {
                trace();
                codeOut.line_(
                        "%s %s { %s ->",
                        toLoopRef(path), operator.getCodeString(), param
                );
                groovyParams.push(param);
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), groovyParams);
                groovyParams.pop();
                codeOut._line("}");
            }
        }
    }

    private boolean toLookupStatement(RecDefNode recDefNode, NodeMapping nodeMapping) {
        OptBox optBox = recDefNode.getOptBox();
        if (optBox == null || optBox.role != ROOT) return false;
        NodeMapping valueNodeMapping = null;
        if (optBox.optList.valuePresent) {
            for (RecDefNode candidate : recDefNode.getChildren()) {
                if (candidate.getTag().equals(optBox.optList.value)) {
                    if (candidate.getNodeMappings().size() == 1) {
                        valueNodeMapping = candidate.getNodeMappings().values().iterator().next();
                    }
                }
            }
        }
        if (nodeMapping.isConstant()) {
            codeOut.line(
                    "%s = lookup%s('%s')",
                    optBox.getOuterOptReference(), optBox.getDictionaryName(nodeMapping.getIndexWithinNode()),
                    nodeMapping.getConstantValue()
            );
        }
        else if (valueNodeMapping == null) {
            codeOut.line(
                    "%s = lookup%s(%s)",
                    optBox.getOuterOptReference(), optBox.getDictionaryName(nodeMapping.getIndexWithinNode()),
                    toGroovyIdentifier(nodeMapping.inputPath.peek())
            );
        }
        else {
            codeOut.line(
                    "%s = lookup%s(%s.%s)",
                    optBox.getOuterOptReference(), optBox.getDictionaryName(nodeMapping.getIndexWithinNode()),
                    toGroovyIdentifier(nodeMapping.inputPath.peek()),
                    toGroovyFirstIdentifier(valueNodeMapping.inputPath.peek())
            );
        }
        codeOut.line_("if (_found%s) {", optBox.optList.dictionary);
        return true;
    }

    private void toMapNodeMapping(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (recDefNode.isLeafElem()) {
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, groovyParams);
            codeOut.end(nodeMapping);
            codeOut._line("}");
        }
        else {
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    trace();
                    codeOut.line("%s %s", sub.getTag().toBuilderCall(), sub.getOptBox().getInnerOptReference());
                }
                else {
                    toElementCode(sub, groovyParams);
                }
            }
            codeOut.end(nodeMapping);
            codeOut._line("}");
        }
    }

    private void toBranchCode(RecDefNode recDefNode, Stack<String> groovyParams) {
        startBuilderCall(recDefNode, false, groovyParams);
        for (RecDefNode sub : recDefNode.getChildren()) {
            if (sub.isAttr()) continue;
            toElementCode(sub, groovyParams);
        }
        codeOut._line("}");
    }

    private void toLeafCode(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (nodeMapping.hasMap()) {
            throw new RuntimeException("Cannot handle map here");
        }
        startBuilderCall(recDefNode, true, groovyParams);
        codeOut.start(nodeMapping);
        toLeafElementCode(nodeMapping, groovyParams);
        codeOut.end(nodeMapping);
        codeOut._line("}");
    }

    private void startBuilderCall(RecDefNode recDefNode, boolean absentFalse, Stack<String> groovyParams) {
        trace();
        if (!recDefNode.hasActiveAttributes()) {
            codeOut.line_("%s%s { %s",
                    prefixFirstBuilder, recDefNode.getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : ""
            );
        }
        else {
            Tag tag = recDefNode.getTag();

            boolean comma = false;
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (!sub.isAttr()) continue;
                OptBox subBox = sub.getOptBox();
                if (subBox != null && subBox.role != ROOT && sub.getNodeMappings().isEmpty()) {
                    if (comma) codeOut.line(",");
                    trace();
                    codeOut.line("%s : %s", sub.getTag().toBuilderCall(), sub.getOptBox().getInnerOptReference());
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.getNodeMappings().values()) {
                        if (comma) codeOut.line(",");
                        trace();

                        codeOut.line_("%s (", tag.toBuilderCall());
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        codeOut.start(nodeMapping);
                        toAttributeCode(nodeMapping, groovyParams);
                        codeOut.end(nodeMapping);
                        codeOut._line("}");

                        comma = true;
                    }
                }
            }
            codeOut._line(") { %s", absentFalse ? ABSENT_IS_FALSE : "").in();
        }
        prefixFirstBuilder = ""; // no longer first
    }

    private void toUserCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (editPath != null) {
            if (!editPath.isGeneratedCode()) {
                String editedCode = editPath.getEditedCode(nodeMapping.recDefNode.getPath());
                if (nodeMapping.isConstant()) {
                    if (editedCode != null) {
                        codeOut.line("'%s'", getConstantFromGroovyCode(stringToLines(editedCode)));
                        return;
                    }
                    else if (nodeMapping.groovyCode != null) {
                        codeOut.line("'%s'", nodeMapping.getConstantValue());
                        return;
                    }
                }
                else {
                    if (editedCode != null) {
                        indentCode(editedCode, codeOut);
                        return;
                    }
                    else if (nodeMapping.groovyCode != null) {
                        indentCode(nodeMapping.groovyCode, codeOut);
                        return;
                    }
                }
            }
        }
        else if (nodeMapping.isConstant()) {
            codeOut.line("'%s'", nodeMapping.getConstantValue());
            return;
        }
        else if (nodeMapping.groovyCode != null) {
            indentCode(nodeMapping.groovyCode, codeOut);
            return;
        }
        toInnerLoop(nodeMapping, getLocalPath(nodeMapping), groovyParams);
    }

    private void toInnerLoop(NodeMapping nodeMapping, Path path, Stack<String> groovyParams) {
        RecDefNode recDefNode = nodeMapping.recDefNode;
        if (path.isEmpty()) throw new RuntimeException();
        if (recDefNode.getOptBox() != null && nodeMapping.hasDictionary()) {
            codeOut.line(recDefNode.getOptBox().getInnerOptReference());
        }
        else if (path.size() == 1) {
            if (nodeMapping.hasMap()) {
                String mapUsage = getMapUsage(nodeMapping);
                codeOut.line(recDefNode.hasFunction() ? String.format("%s(%s.toString())", recDefNode.getFunction(), mapUsage) : mapUsage);
            }
            else {
                if (nodeMapping.isConstant()) {
                    codeOut.line("'%s'", nodeMapping.getConstantValue());
                }
                else if (recDefNode.hasFunction()) {
                    codeOut.line("\"${%s(%s)}\"", recDefNode.getFunction(), toLeafGroovyParam(path));
                }
                else {
                    codeOut.line("\"${%s}\"", toLeafGroovyParam(path));
                }
            }
        }
        else if (recDefNode.isLeafElem()) {
            toInnerLoop(nodeMapping, path.withRootRemoved(), groovyParams);
        }
        else {
            if (nodeMapping.hasMap()) {
                throw new RuntimeException("Unable to handle map here");
            }
            String param = toLoopGroovyParam(path);
            boolean needLoop = !groovyParams.contains(param);
            if (needLoop) {
                trace();
                codeOut.line_(
                        "%s %s { %s ->",
                        toLoopRef(path), nodeMapping.getOperator().getCodeString(), param
                );
            }
            toInnerLoop(nodeMapping, path.withRootRemoved(), groovyParams);
            if (needLoop) codeOut._line("}");
        }
    }

    private String getMapUsage(NodeMapping nodeMapping) {
        if (!nodeMapping.hasMap()) return null;
        StringBuilder usage = new StringBuilder("\"");
        Iterator<Path> walk = nodeMapping.getInputPaths().iterator();
        while (walk.hasNext()) {
            Path path = walk.next();
            usage.append(String.format("${%s['%s']}", getMapName(nodeMapping), path.peek().toMapKey()));
            if (walk.hasNext()) usage.append(" ");
        }
        usage.append("\"");
        return usage.toString();
    }

    private String getMapName(NodeMapping nodeMapping) {
        return String.format("_M%d", nodeMapping.inputPath.size());
    }

    private void toAttributeCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (!nodeMapping.recDefNode.isAttr()) return;
        toUserCode(nodeMapping, groovyParams);
    }

    private void toLeafElementCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (nodeMapping.recDefNode.isAttr() || !nodeMapping.recDefNode.isLeafElem()) {
            throw new IllegalStateException("Not a leaf element!");
        }
        toUserCode(nodeMapping, groovyParams);
    }

    private Set<Path> getSiblingInputPathsOfChildren(RecDefNode recDefNode) {
        List<NodeMapping> subMappings = new ArrayList<>();
        for (RecDefNode sub : recDefNode.getChildren()) sub.collectNodeMappings(subMappings);
        Set<Path> inputPaths = new TreeSet<>();
        Path parent = null;
        for (NodeMapping subMapping : subMappings) {
            if (parent == null) {
                parent = subMapping.inputPath.parent();
            }
            else {
                if (!subMapping.inputPath.parent().equals(parent)) return null; // different parents
            }
            inputPaths.addAll(subMapping.getInputPaths());
        }
        return inputPaths;
    }

    private Path getLocalPath(NodeMapping nodeMapping) {
        NodeMapping ancestor = getAncestorNodeMapping(nodeMapping, nodeMapping.inputPath);
        if (ancestor.inputPath.isAncestorOf(nodeMapping.inputPath)) {
            return nodeMapping.inputPath.extendAncestor(ancestor.inputPath);
        }
        else {
            return nodeMapping.inputPath;
        }
    }

    private NodeMapping getAncestorNodeMapping(NodeMapping nodeMapping, Path path) {
        for (RecDefNode ancestor = nodeMapping.recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping ancestorNodeMappings : ancestor.getNodeMappings().values()) {
                if (ancestorNodeMappings.inputPath.isAncestorOf(path)) return ancestorNodeMappings;
            }
        }
        return new NodeMapping().setInputPath(Path.create("input")).setOutputPath(nodeMapping.outputPath.takeFirst());
    }

    private void toLookupCode(NodeMapping nodeMapping) {
        OptBox optBox = nodeMapping.recDefNode.getOptBox();
        int index = nodeMapping.getIndexWithinNode();
        if (nodeMapping.hasDictionary()) {
            if (optBox == null || optBox.role == CHILD) return;
            codeOut.line_(String.format("def Dictionary%s = [", optBox.getDictionaryName(index)));
            Iterator<Map.Entry<String, String>> walk = nodeMapping.dictionary.entrySet().iterator();
            while (walk.hasNext()) {
                Map.Entry<String, String> entry = walk.next();
                codeOut.line(String.format("'''%s''':'''%s'''%s",
                        sanitizeGroovy(entry.getKey()),
                        sanitizeGroovy(entry.getValue()),
                        walk.hasNext() ? "," : ""
                ));
            }
            codeOut._line("]");
            codeOut.line_("def lookup%s = { value ->", optBox.getDictionaryName(index));
            codeOut.line("   if (!value) return null");
            codeOut.line("   String optKey = Dictionary%s[value.sanitize()]", optBox.getDictionaryName(index));
            codeOut.line("   if (!optKey) optKey = value");
            codeOut.line("   _optLookup['%s'][optKey]", optBox.getDictionaryName());
            codeOut._line("}");
        }
        else {
            if (optBox == null || optBox.optList == null || optBox.optList.valuePresent) return;
            codeOut.line_("def lookup%s = { value ->", optBox.getDictionaryName(index));
            codeOut.line("   if (!value) return null");
            codeOut.line("   _optLookup['%s'][value.toString()]", optBox.getDictionaryName());
            codeOut._line("}");
        }
    }

    private String toLoopGroovyParam(Path path) {
        Tag inner = path.getTag(1);
        return toGroovyIdentifier(inner);
    }

    private String toLeafGroovyParam(Path path) {
        Tag inner = path.getTag(0);
        return toGroovyIdentifier(inner);
    }

    private String toMapExpression(NodeMapping nodeMapping, Path path) {
        List<Path> paths = nodeMapping.getInputPaths();
        StringBuilder expression = new StringBuilder("(");
        Iterator<Path> walk = paths.iterator();
        while (walk.hasNext()) {
            Path inputPath = walk.next();
            Path loopPath = path.parent().child(inputPath.peek());
            if (loopPath.size() != 2) throw new RuntimeException("Path must have length two");
            expression.append(toLoopRef(loopPath));
            if (walk.hasNext()) expression.append(" | ");
        }
        expression.append(")");
        return expression.toString();
    }

    private String toLoopRef(Path path) {
        Tag outer = path.getTag(0);
        Tag inner = path.getTag(1);
        if (outer == null || inner == null) throw new RuntimeException("toLoopRef called on " + path);
        return toGroovyIdentifier(outer) + toGroovyReference(inner);
    }

    private static String toGroovyReference(Tag tag) {
        return tag.isAttribute() ? String.format("['@%s']", tag.toString()) : "." + tagToVariable(tag.toString());
    }

    private String sanitizeGroovy(String thing) {
        return thing.replaceAll("'", "\\\\'")
                .replaceAll("\n", " ")
                .replaceAll(" +", " ");
    }

    private List<String> getIfAbsentCode(List<String> groovyCode) {
        List<String> code = null;
        if (groovyCode != null) {
            int braceLevel = 0;
            for (String line : groovyCode) {
                if (code != null) {
                    braceLevel += braceCount(line);
                    if (braceLevel <= 0) break;
                    code.add(line);
                }
                else if (StringUtil.IF_ABSENT_PATTERN.matcher(line).matches()) {
                    code = new ArrayList<>();
                    braceLevel++;
                }
            }
        }
        return code;
    }

    private int braceCount(String line) {
        int count = 0;
        for (int walk = 0; walk < line.length(); walk++) {
            switch (line.charAt(walk)) {
                case '{':
                    count++;
                    break;
                case '}':
                    count--;
                    break;
            }
        }
        return count;
    }

    private void trace() {
        if (!trace) return;
        StringBuilder out = new StringBuilder("// CodeGenerator.java trace: ");
        Iterator<StackTraceElement> stack = Arrays.asList(Thread.currentThread().getStackTrace()).iterator();
        stack.next();
        stack.next();
        while (stack.hasNext()) {
            StackTraceElement element = stack.next();
            if (!"CodeGenerator.java".equals(element.getFileName())) continue;
            out.append(' ').append(String.valueOf(element.getLineNumber()));
        }
        codeOut.line(out.toString());
    }
}

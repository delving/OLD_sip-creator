/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Generate code snippets for field mappings
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CodeGenerator {

    public static boolean isDictionaryPossible(FieldDefinition fieldDefinition, StatsTreeNode node) {
        return fieldDefinition.hasOptions() && node.getStatistics().getHistogramValues() != null;
    }

    public List<FieldMapping> createObviousMappings(List<FieldDefinition> unmappedFieldDefinitions, List<SourceVariable> variables, List<FactDefinition> factDefinitions) {
        List<FieldMapping> fieldMappings = new ArrayList<FieldMapping>();
        FieldMapping uniqueMapping = createUniqueMapping(unmappedFieldDefinitions, variables);
        if (uniqueMapping != null) {
            fieldMappings.add(uniqueMapping);
        }
        for (FieldDefinition fieldDefinition : unmappedFieldDefinitions) {
            if (fieldDefinition.factName != null) {
                FieldMapping fieldMapping = createObviousMappingFromFact(fieldDefinition, factDefinitions);
                if (fieldMapping != null) {
                    fieldMappings.add(fieldMapping);
                }
            }
            else {
                for (SourceVariable variable : variables) {
                    String variableName = variable.getVariableName();
                    String fieldName = fieldDefinition.getFieldNameString();
                    if (variableName.endsWith(fieldName)) {
                        FieldMapping fieldMapping = createObviousMappingFromVariable(fieldDefinition, variables);
                        if (fieldMapping != null) {
                            fieldMappings.add(fieldMapping);
                        }
                    }
                }
            }
        }
        return fieldMappings;
    }

    public void generateCodeFor(FieldMapping fieldMapping, SourceVariable sourceVariable, boolean dictionaryPreferred) {
        fieldMapping.clearCode();
        if (isDictionaryPossible(fieldMapping.getDefinition(), sourceVariable.getNode()) && dictionaryPreferred) {
            eachLookupBlock(fieldMapping, sourceVariable.getVariableName());
            fieldMapping.createDictionary(sourceVariable.getNode().getStatistics().getHistogramValues());
        }
        else {
            eachBlock(fieldMapping, sourceVariable);
        }
    }

    public void generateCodeFor(FieldMapping fieldMapping, List<SourceVariable> sourceVariables, String constantValue) {
        fieldMapping.clearCode();
        switch (sourceVariables.size()) {
            case 0:
                lineConstant(fieldMapping, constantValue);
                break;
            case 1:
                generateCodeFor(fieldMapping, sourceVariables.get(0), false);
                break;
            default:
                eachBlock(fieldMapping, createBracketedExpression(sourceVariables));
                break;
        }
    }

    // ===================== the rest is private

    private String createBracketedExpression(List<SourceVariable> vars) {
        StringBuilder out = new StringBuilder("(");
        Iterator<SourceVariable> walk = vars.iterator();
        while (walk.hasNext()) {
            SourceVariable var = walk.next();
            out.append(var.getNode().getVariableName());
            if (walk.hasNext()) {
                out.append(" + ");
            }
        }
        out.append(")");
        return out.toString();
    }

    private FieldMapping createUniqueMapping(List<FieldDefinition> unmappedFieldDefinitions, List<SourceVariable> variables) {
        for (SourceVariable variable : variables) {
            if (variable.getNode().isUniqueElement()) {
                for (FieldDefinition definition : unmappedFieldDefinitions) {
                    if (definition.identifierField) {
                        FieldMapping fieldMapping = new FieldMapping(definition);
                        eachBlock(fieldMapping, variable);
                        return fieldMapping;
                    }
                }
            }
        }
        return null;  //To change body of created methods use File | Settings | File Templates.
    }

    private FieldMapping createObviousMappingFromFact(FieldDefinition fieldDefinition, List<FactDefinition> factDefinitions) {
        FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
        for (FactDefinition factDefinition : factDefinitions) {
            if (factDefinition.name.equals(fieldDefinition.factName)) {
                line(fieldMapping, factDefinition.name);
            }
        }
        return fieldMapping.code == null ? null : fieldMapping;
    }

    private FieldMapping createObviousMappingFromVariable(FieldDefinition fieldDefinition, List<SourceVariable> variables) {
        FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
        for (SourceVariable variable : variables) {
            String variableName = variable.getVariableName();
            String fieldName = fieldDefinition.getFieldNameString();
            if (variableName.endsWith(fieldName)) {
                eachBlock(fieldMapping, variable);
            }
        }
        return fieldMapping.code == null ? null : fieldMapping;
    }

    private void eachBlock(FieldMapping fieldMapping, SourceVariable sourceVariable) {
        fieldMapping.addCodeLine(String.format("%s * {", fieldMapping.getDefinition().addOptionalConverter(sourceVariable.getVariableName())));
        for (String attribute : sourceVariable.getAttributeNames()) {
            attributeComment(fieldMapping, attribute);
        }
        line(fieldMapping, "it");
        fieldMapping.addCodeLine("}");
    }

    private void eachBlock(FieldMapping fieldMapping, String expression) {
        fieldMapping.addCodeLine(String.format("%s * {", fieldMapping.getDefinition().addOptionalConverter(expression)));
        line(fieldMapping, "it");
        fieldMapping.addCodeLine("}");
    }

    private void eachLookupBlock(FieldMapping fieldMapping, String source) {
        fieldMapping.addCodeLine(String.format("%s * {", fieldMapping.getDefinition().addOptionalConverter(source)));
        fieldMapping.addCodeLine(
                String.format(
                        "%s.%s %s_lookup(it)",
                        fieldMapping.getDefinition().getPrefix(),
                        fieldMapping.getDefinition().getLocalName(),
                        fieldMapping.getDefinition().getFieldNameString()
                )
        );
        fieldMapping.addCodeLine("}");
    }

    private void lineConstant(FieldMapping fieldMapping, String constantValue) {
        line(fieldMapping, String.format("'%s'", constantValue));
    }

    private void line(FieldMapping fieldMapping, String parameter) {
        fieldMapping.addCodeLine(
                String.format(
                        "%s.%s %s",
                        fieldMapping.getDefinition().getPrefix(),
                        fieldMapping.getDefinition().getLocalName(),
                        parameter
                )
        );
    }

    private void attributeComment(FieldMapping fieldMapping, String attributeName) {
        fieldMapping.addCodeLine(String.format("// attribute available: it.'%s'",attributeName));
    }

}

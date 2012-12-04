/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.xml.Schema;
import eu.delving.schema.xml.Version;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Provide an form interface for creating datasets
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetStandaloneFrame extends FrameBase {
    private static final String UNSELECTED = "<select>";
    private static final FactDefinition SCHEMA_VERSIONS_FACT = new FactDefinition("schemaVersions", "Schema Versions");
    private static final JLabel EMPTY_LABEL = new JLabel("Fetching...", JLabel.CENTER);
    private static final String FACTS_PREFIX = "facts";
    private SchemaRepository schemaRepository;
    private List<FactDefinition> factDefinitions;
    private Map<String, FieldComponent> fieldComponents = new TreeMap<String, FieldComponent>();
    private JList dataSetList = new JList();
    private JPanel fieldPanel = new JPanel();

    public DataSetStandaloneFrame(SipModel sipModel, SchemaRepository schemaRepository) {
        super(Which.DATA_SET, sipModel, "Data set facts");
        this.schemaRepository = schemaRepository;
        fieldPanel.add(EMPTY_LABEL);
        sipModel.exec(createFactDefFetcher());
    }

    @Override
    protected void buildContent(Container content) {
        fieldPanel.setBorder(BorderFactory.createTitledBorder("Facts"));
        content.add(fieldPanel, BorderLayout.EAST);
        content.add(SwingHelper.scrollV("Data sets", dataSetList), BorderLayout.CENTER);
        sipModel.exec(createFormBuilder());
    }

    private Work createFactDefFetcher() {
        return new Work() {

            @Override
            public Job getJob() {
                return Job.FETCH_FACTS_DEF;
            }

            @Override
            public void run() {
                try {
                    String factsString = schemaRepository.getSchema(new SchemaVersion(FACTS_PREFIX, "1.0.0"), SchemaType.FACT_DEFINITIONS);
                    final XStream xstream = new XStream();
                    xstream.processAnnotations(FactDefinitionList.class);
                    FactDefinitionList factDefinitionList = (FactDefinitionList) xstream.fromXML(factsString);
                    factDefinitions = new ArrayList<FactDefinition>();
                    factDefinitions.addAll(factDefinitionList.definitions);
                    factDefinitions.add(SCHEMA_VERSIONS_FACT);
                }
                catch (IOException e) {
                    sipModel.getFeedback().alert("Unable to fetch dataset facts", e);
                }
            }
        };
    }

    private Swing createFormBuilder() {
        return new Swing() {
            @Override
            public void run() {
                fieldPanel.removeAll();
                FormLayout layout = new FormLayout(
                        "right:pref, 3dlu, pref",
                        createRowLayout(factDefinitions)
                );
                PanelBuilder pb = new PanelBuilder(layout);
                pb.border(Borders.DIALOG);
                CellConstraints cc = new CellConstraints();
                int count = 2;
                for (FactDefinition factDefinition : factDefinitions) {
                    pb.addLabel(factDefinition.prompt, cc.xy(1, count));
                    if (factDefinition.options == null) {
                        JTextField field = new JTextField(30);
                        pb.add(field, cc.xy(3, count));
                        if (factDefinition == SCHEMA_VERSIONS_FACT) {
                            field.setToolTipText(createSchemaVersionToolTip());
                        }
                        fieldComponents.put(factDefinition.name, new FieldComponent(field));
                    }
                    else {
                        JComboBox comboBox = new JComboBox(factDefinition.getOptions());
                        pb.add(comboBox, cc.xy(3, count));
                        fieldComponents.put(factDefinition.name, new FieldComponent(comboBox));
                    }
                    count += 2;
                }
                fieldPanel.add(pb.getPanel(), BorderLayout.CENTER);
                fieldPanel.revalidate();
            }

        };
    }

    private String createSchemaVersionToolTip() {
        List<SchemaVersion> list = new ArrayList<SchemaVersion>();
        for (Schema schema : schemaRepository.getSchemas()) {
            if (FACTS_PREFIX.equals(schema.prefix)) continue;
            for (Version version : schema.versions) {
                list.add(new SchemaVersion(schema.prefix, version.number));
            }
        }
        StringBuilder out = new StringBuilder("<html><h2>Schema Version Choices:</h2>\n");
        out.append("<p>This field must be comma-separated list<br>of the items below.</p>\n");
        out.append("<ul>\n");
        for (SchemaVersion schemaVersion : list) {
            out.append("<li>").append(schemaVersion).append("</li>\n");
        }
        out.append("</ul>\n</html>");
        return out.toString();
    }

    private String createRowLayout(List<FactDefinition> factDefinitions) {
        StringBuilder out = new StringBuilder("3dlu");
        int size = factDefinitions.size();
        while (size-- > 0) out.append(", pref, 3dlu");
        return out.toString();
    }

    private class FieldComponent {
        private JTextField textField;
        private JComboBox comboBox;

        private FieldComponent(JTextField textField) {
            this.textField = textField;
        }

        private FieldComponent(JComboBox comboBox) {
            this.comboBox = comboBox;
        }

        public boolean isTextField() {
            return textField != null;
        }

        public String getValue() {
            return isTextField() ? textField.getText() : (String) comboBox.getSelectedItem();
        }

        public void setValue(String value) {
            if (isTextField()) {
                textField.setText(value);
            }
            else {
                comboBox.setSelectedItem(value);
            }
        }

        public void setEditable(boolean editable) {
            if (isTextField()) {
                textField.setEditable(editable);
            }
            else {
                comboBox.setEditable(editable);
            }
        }
    }

    public void setFacts(final Map<String, String> facts) {
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                if (factDefinitions == null) return;
                for (FactDefinition factDefinition : factDefinitions) {
                    FieldComponent fieldComponent = fieldComponents.get(factDefinition.name);
                    if (fieldComponent == null) throw new RuntimeException("No field component for " + factDefinition.name);
                    String value = facts.get(factDefinition.name);
                    if (value == null) value = "";
                    fieldComponent.setValue(value);
                    fieldComponent.setEditable(false);
                }
            }
        });
    }

    public Map<String, String> getFacts() {
        Map<String, String> facts = new TreeMap<String, String>();
        for (FactDefinition factDefinition : factDefinitions) {
            FieldComponent fieldComponent = fieldComponents.get(factDefinition.name);
            facts.put(factDefinition.name, fieldComponent.getValue());
        }
        return facts;
    }

    @XStreamAlias("fact-definition-list")
    public static class FactDefinitionList {
        @XStreamImplicit
        public List<FactDefinition> definitions;
    }

    @XStreamAlias("fact-definition")
    public static class FactDefinition {

        @XStreamAsAttribute
        public String name;

        public String prompt;

        public List<String> options;

        public FactDefinition() {
        }

        public FactDefinition(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        public String[] getOptions() {
            String[] array = new String[options.size() + 1];
            int index = 0;
            array[index++] = UNSELECTED;
            for (String option : options) array[index++] = option;
            return array;
        }
    }
    
    private class DataSetListModel extends AbstractListModel {
        private List<DataSet> dataSets;

        public void refresh() {
            List<DataSet> freshDataSets = new ArrayList<DataSet>();
            freshDataSets.addAll(sipModel.getStorage().getDataSets().values());
            Collections.sort(freshDataSets);
            if (getSize() > 0) {
                int sizeWas = getSize();
                dataSets = null;
                fireIntervalRemoved(this, 0, sizeWas);
            }
            dataSets = freshDataSets;
            fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return dataSets == null ? 0 : dataSets.size();
        }

        @Override
        public Object getElementAt(int i) {
            return dataSets == null ? null : dataSets.get(i);
        }
    }
}

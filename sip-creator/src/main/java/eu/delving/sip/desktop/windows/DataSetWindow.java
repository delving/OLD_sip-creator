/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.desktop.windows;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.util.GridBagHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains the following elements:
 *
 * <ul>
 * <li>List of data sets</li>
 * <li>Search component for datasets</li>
 * <li>Change log</li>
 * <ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DataSetWindow extends DesktopWindow {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String TITLE_LABEL = "Data sets";
    private JLabel title = new JLabel(TITLE_LABEL);
    private JTable dataSets;
    private JTextField filter = new JTextField("Filter");
    private JButton select = new JButton("Select");
    private JButton cancel = new JButton("Cancel");
    private DataSetModel<FileStore.DataSetStore> dataSetModel;

    public DataSetWindow(SipModel sipModel) {
        super(sipModel);
        setLayout(new GridBagLayout());
        buildLayout();
        addActions();
    }

    private SipModel.UpdateListener updateListener = new SipModel.UpdateListener() {

        @Override
        public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
            LOG.info("Updated data set store " + dataSetStore);
        }

        @Override
        public void updatedStatistics(FieldStatistics fieldStatistics) {
            LOG.info("Updated field statistics " + fieldStatistics);
        }

        @Override
        public void updatedRecordRoot(Path recordRoot, int recordCount) {
            LOG.info("Updated record root " + recordRoot + " " + recordCount);
        }

        @Override
        public void normalizationMessage(boolean complete, String message) {
            LOG.info("Normalization : " + complete + " " + message);
        }
    };

    private void addActions() {
        sipModel.addUpdateListener(updateListener);
        select.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        DataSetModel<FileStore.DataSetStore> model = dataSetModel;
                        FileStore.DataSetStore selectedItem = model.getSelectedItem(dataSets.getSelectedRow());
                        dataSetChangeListener.dataSetIsChanging(selectedItem);
                        LOG.info(selectedItem.getSourceFile() + " exists? " + selectedItem.getSourceFile().exists());
                        if (!selectedItem.hasSource()) {
                            LOG.warn("No source found, download it first");
                        }
                        int result = JOptionPane.showConfirmDialog(
                                DataSetWindow.this,
                                String.format("<html>You are switching to <b>%s</b>. Are you sure?<br/>Your current workspace will be saved.</html>",
                                        selectedItem.getSpec()),
                                "Change data set",
                                JOptionPane.YES_NO_OPTION
                        );
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                dataSetChangeListener.dataSetHasChanged(selectedItem);
                                setVisible(false);
                                break;
                            default:
                                dataSetChangeListener.dataSetChangeCancelled(selectedItem);
                                break;
                        }
                    }
                }
        );
    }

    private void buildLayout() {
        GridBagHelper g = new GridBagHelper();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.reset();
        add(title, g);
        dataSetModel = new DataSetModel<FileStore.DataSetStore>(fetchDataSets());
        dataSets = new JTable(dataSetModel);
        dataSets.setSize(new Dimension(700, 400));
        dataSets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSets.setDefaultRenderer(Object.class, new ColorRenderer());
        g.line();
        g.gridwidth = 2;
        add(new JScrollPane(dataSets), g);
        g.gridwidth = 1;
        g.line();
        add(cancel, g);
        g.right();
        add(select, g);
    }

    private class DataSetModel<T extends FileStore.DataSetStore> extends AbstractTableModel {

        private String[] columnHeaders = {"Collection", "Cached", "Count", "Validated"};
        private List<T> data = new ArrayList<T>();

        private DataSetModel(List<T> data) {
            this.data = data;
        }

        public T getSelectedItem(int i) {
            return data.get(i);
        }

        @Override
        public String getColumnName(int i) {
            return columnHeaders[i];
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.length;
        }

        @Override
        public Object getValueAt(int row, int col) {
            T dataSetInfo = data.get(row);
            Object o;
            switch (col) {
                case 0:
                    o = dataSetInfo.getSpec();
                    break;
                case 1:
                    o = dataSetInfo.hasSource();
                    break;
                case 2:
                    o = StringUtils.isEmpty(dataSetInfo.getFacts().getRecordCount()) ? "-" : dataSetInfo.getFacts().getRecordCount();
                    break;
                case 3:
                    o = dataSetInfo.getFacts().isValid();
                    break;
                default:
                    o = "-";
                    break;
            }
            return o;
        }
    }

    private class ColorRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object o, boolean selected, boolean hasFocus, int row, int col) {
            Component component = super.getTableCellRendererComponent(table, o, selected, hasFocus, row, col);
            if (selected) {
                component.setBackground(Color.MAGENTA);
                return component;
            }
            if (row % 2 == 0) {
                component.setBackground(Color.decode("0xCCF5FF"));
            }
            else {
                component.setBackground(Color.WHITE);
            }
            return component;
        }
    }

    private List<FileStore.DataSetStore> fetchDataSets() {
        List<FileStore.DataSetStore> data = new ArrayList<FileStore.DataSetStore>();
        Map<String, FileStore.DataSetStore> dataSetStores = sipModel.getFileStore().getDataSetStores();
        for (Map.Entry<String, FileStore.DataSetStore> entry : dataSetStores.entrySet()) {
            FileStore.DataSetStore dataSetStore = entry.getValue();
            data.add(dataSetStore);
        }
        return data;
    }
}

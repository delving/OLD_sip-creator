/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

import eu.delving.metadata.Path;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.*;
import eu.delving.sip.xml.ChartHelper;
import eu.delving.sip.xml.Stats;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import static eu.delving.sip.base.SwingHelper.scrollV;

/**
 * Show statistics in an html panel, with special tricks for separately threading the html generation
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatisticsFrame extends FrameBase {
    private final String EMPTY = "<html><center><h3>No Statistics</h3><b>Select an item with statistics</b><br><br>";
    private JLabel summaryLabel = new JLabel(EMPTY, JLabel.CENTER);
    private ChartHelper chartHelper;
    private RandomSampleModel sampleModel = new RandomSampleModel();
    private JComponent samplePanel = scrollV("Sample", new JList(sampleModel));
    private HistogramModel histogramModel = new HistogramModel();
    private JComponent histogramPanel = scrollV("Histogram", new JList(histogramModel));
    private JPanel wordCountPanel = emptyPanel();
    private JPanel fieldFrequencyPanel = emptyPanel();
    private JPanel presentAbsentPanel = emptyPanel();

    public StatisticsFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.STATISTICS, desktop, sipModel, "Statistics", false);
        summaryLabel.setFont(new Font(summaryLabel.getFont().getFamily(), Font.BOLD, summaryLabel.getFont().getSize()));
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                if (!transition.sourceChanged) return;
                SortedSet<SourceTreeNode> nodes = createModel.getSourceTreeNodes();
                if (nodes != null && nodes.size() == 1) {
                    SourceTreeNode node = nodes.iterator().next();
                    final Path path = node.getPath(true);
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            setPath(path);
                        }
                    });
                }
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                // todo: clear
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                switch (dataSetState) {
                    case ABSENT:
                    case EMPTY:
                    case IMPORTED:
                        setStats(null);
                        break;
                    case ANALYZED_IMPORT:
                    case DELIMITED:
                    case SOURCED:
                        setStats(dataSet.getStats(false, null));
                        break;
                    case MAPPING:
                    case ANALYZED_SOURCE:
                        setStats(dataSet.getStats(true, null));
                        break;
                    case VALIDATED:
                        break;
                }
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        add(createSummary(), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
    }

    public void setStats(Stats stats) {
        Exec.checkSwing();
        this.chartHelper = stats == null ? null : new ChartHelper(stats);
        if (chartHelper != null && chartHelper.hasPresentAbsentChart()) {
            setPanelContent(presentAbsentPanel, chartHelper.getPresentAbsentChart());
        }
        setPath(null);
    }

    public void setPath(Path path) {
        Exec.checkSwing();
        if (path != null) {
            if (chartHelper == null) return;
            Stats.ValueStats valueStats = chartHelper.setPath(path);
            setSummary(path, valueStats);
            sampleModel.setSample(valueStats == null ? null : valueStats.sample);
            histogramModel.setHistogram(valueStats == null ? null : valueStats.values);
            setPanelContent(fieldFrequencyPanel, chartHelper.hasFrequencyChart() ? chartHelper.getFieldFrequencyChart() : emptyLabel());
            setPanelContent(wordCountPanel, chartHelper.hasFrequencyChart() ? chartHelper.getWordCountChart() : emptyLabel());
        }
        else {
            setSummary(null, null);
            sampleModel.setSample(null);
            setPanelContent(fieldFrequencyPanel, emptyLabel());
            setPanelContent(wordCountPanel,emptyLabel());
        }
    }

    private void setSummary(Path path, Stats.ValueStats valueStats) {
        if (valueStats == null) {
            summaryLabel.setText(EMPTY);
        }
        else {
            summaryLabel.setText(String.format("<html><center><h3>%s</h3><b>%s</b><br><br>", path, valueStats.getSummary()));
        }
    }

    private JComponent createSummary() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Summary"));
        p.add(summaryLabel, BorderLayout.CENTER);
        p.setMinimumSize(new Dimension(300, 400));
        return p;
    }

    private JComponent createCenter() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Present/ Absent", presentAbsentPanel);
        tabs.addTab("Sample", samplePanel);
        tabs.addTab("Histogram", histogramPanel);
        tabs.addTab("Word Count", scrollV(wordCountPanel));
        tabs.addTab("Field Frequency", scrollV(fieldFrequencyPanel));
        return tabs;
    }

    private void setPanelContent(JPanel panel, JComponent content) {
        if (!(panel.getLayout() instanceof BoxLayout)) throw new RuntimeException();
        panel.removeAll();
        panel.add(content);
        panel.add(Box.createVerticalGlue());
        panel.validate();
    }

    private JPanel emptyPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        setPanelContent(p, emptyLabel());
        return p;
    }

    private JComponent emptyLabel() {
        return new JLabel("Empty", JLabel.CENTER);
    }

    private static class HistogramModel extends AbstractListModel {
        private List<Stats.Counter> list = new ArrayList<Stats.Counter>();

        public void setHistogram(Stats.Histogram histogram) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (histogram != null) {
                list.addAll(histogram.counterMap.values());
                Collections.sort(list);
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            Stats.Counter counter = list.get(i);
            return String.format("   %d (%s) : '%s'", counter.count, counter.percentage, counter.value);
        }
    }

    private class RandomSampleModel extends AbstractListModel {

        private List<String> list = new ArrayList<String>();

        public void setSample(Stats.Sample sample) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (sample != null) {
                list.addAll(sample.values);
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            return "   " + list.get(i);
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(400, 250);
    }
}

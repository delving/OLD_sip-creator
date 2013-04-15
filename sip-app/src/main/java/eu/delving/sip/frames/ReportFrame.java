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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.scrollV;

/**
 * This frame shows the contents of the ReportFileModel which can contain multiple validation reports, shown in
 * multiple tabs.  The idea is that only after viewing these reports should you be deciding to upload a mapping.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFrame extends FrameBase implements ReportFileModel.Listener {
    public static final JLabel EMPTY_LABEL = new JLabel("No reports available", JLabel.CENTER);
    private JPanel center = new JPanel(new BorderLayout());

    public ReportFrame(SipModel sipModel) {
        super(Which.REPORT, sipModel, "Report");
        center.add(EMPTY_LABEL);
        sipModel.getReportFileModel().setListener(this);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(center, BorderLayout.CENTER);
    }

    @Override
    public void reportsUpdated(ReportFileModel reportFileModel) {
        List<ReportFile> reports = reportFileModel.getReports();
        center.removeAll();
        switch (reports.size()) {
            case 0:
                center.add(EMPTY_LABEL, BorderLayout.CENTER);
                break;
            case 1:
                center.add(createReportPanel(reports.get(0)), BorderLayout.CENTER);
                break;
            default:
                JTabbedPane tabs = new JTabbedPane();
                for (ReportFile report : reports) {
                    tabs.addTab(report.getPrefix().toUpperCase(), createReportPanel(report));
                }
                center.add(tabs, BorderLayout.CENTER);
                break;
        }
        center.validate();
    }

    private JComponent createReportPanel(ReportFile report) {
        final JList list = report.createJList();
        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                ReportFile.Rec rec = (ReportFile.Rec) list.getSelectedValue();
                if (rec == null) return;
                Work work = rec.checkLinks(sipModel.getFeedback());
                if (work == null) return;
                sipModel.exec(work);
            }
        });
        return scrollV("Report", list);
    }


}

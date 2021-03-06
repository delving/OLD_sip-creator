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

package eu.delving.sip.model;

import eu.delving.sip.base.Swing;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.files.StorageException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * A list model for showing the contents of the validation files, one for each mapping.  This class rather naively
 * just loads all of the lines of the file, which could be problematic if it is very large.  It should be made
 * more clever when time permits.
 *
 *
 */

public class ReportFileModel {
    private SipModel sipModel;
    private Listener listener;
    private ReportFile reportFile;

    public interface Listener {
        void reportsUpdated(ReportFileModel reportFileModel);
    }

    public ReportFileModel(final SipModel sipModel) {
        this.sipModel = sipModel;
        Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (reportFile != null) {
                    List<ReportFile.Rec> fetch = reportFile.prepareFetch();
                    if (!fetch.isEmpty()) {
                        sipModel.exec(reportFile.fetchRecords(fetch, sipModel.getFeedback()));
                    }
                    reportFile.maintainCache();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public ReportFile getReport() {
        return reportFile;
    }

    public void refresh() {
        sipModel.getFeedback().info("Report file model refreshing");
        if (sipModel.getDataSetModel().isEmpty()) {
            return;
        }
        if (reportFile != null) {
            reportFile.close();
            reportFile = null;
        }
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        try {
            ReportFile reportFile = dataSet.getReport();
            if (reportFile != null) {
                sipModel.getFeedback().info("There is a report file");
                ReportFileModel.this.reportFile = reportFile;
            }
            else {
                sipModel.getFeedback().info("There is no report file");
            }
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Cannot read report file", e);
        }
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                listener.reportsUpdated(ReportFileModel.this);
            }
        });
    }

    public void shutdown() {
        reportFile.close();
    }

}
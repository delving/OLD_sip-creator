/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.menus;

import eu.delving.sip.base.Exec;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Special functions for experts
 *
 * @author Gerald de Jong, Beautiful Code BV, <gerald@delving.eu>
 */

public class ExpertMenu extends JMenu {
    private SipModel sipModel;
    private JDesktopPane desktop;

    public ExpertMenu(final SipModel sipModel, JDesktopPane desktop) {
        super("Expert");
        this.sipModel = sipModel;
        this.desktop = desktop;
        add(new MaxUniqueValueLengthAction());
        add(new UniqueConverterAction());
        add(new WriteOutputAction());
    }

    private class MaxUniqueValueLengthAction extends AbstractAction {

        private MaxUniqueValueLengthAction() {
            super("Set Maximum Unique Value Length");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = JOptionPane.showInputDialog(
                    desktop,
                    "Enter the maximum length for unique element value",
                    String.valueOf(Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH)
            );
            if (answer != null) {
                answer = answer.trim();
                try {
                    int max = Integer.parseInt(answer);
                    sipModel.getStatsModel().setMaxUniqueValueLength(max);
                }
                catch (NumberFormatException e) {
                    sipModel.getFeedback().alert("Not a number: " + answer);
                }
            }
        }
    }

    private class UniqueConverterAction extends AbstractAction {

        private UniqueConverterAction() {
            super("Set Converter for the Unique value");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = JOptionPane.showInputDialog(
                    desktop,
                    String.format(
                            "Enter a regular expression (executed by String.replaceFirst) in the form of 'from%sto'",
                            SourceConverter.CONVERTER_DELIMITER
                    ),
                    sipModel.getStatsModel().getUniqueValueConverter()
            );
            if (answer != null) {
                answer = answer.trim();
                sipModel.getStatsModel().setUniqueValueConverter(answer);
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sipModel.getDataSetModel().getDataSet().deleteSource();
                        }
                        catch (StorageException e) {
                            sipModel.getFeedback().alert("Unable to delete source", e);
                        }
                    }
                });
            }
        }
    }

    private class WriteOutputAction extends AbstractAction {

        private WriteOutputAction() {
            super("Write XML output of the validation");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = JOptionPane.showInputDialog(
                    desktop,
                    "Enter the directory where output is to be stored",
                    sipModel.getPreferences().get(FileProcessor.OUTPUT_FILE_PREF, "")
            );
            if (answer != null) {
                answer = answer.trim();
                File directory = new File(answer);
                if (!directory.exists()) {
                    failedAnswer(answer + " doesn't exist");
                }
                else if (!directory.isDirectory()) {
                    failedAnswer(answer + " is not a directory");
                }
                else {
                    sipModel.getPreferences().put(FileProcessor.OUTPUT_FILE_PREF, directory.getAbsolutePath());
                }
            }
        }

        private void failedAnswer(String message) {
            sipModel.getFeedback().alert(message);
            sipModel.getPreferences().put(FileProcessor.OUTPUT_FILE_PREF, "");
        }
    }
}

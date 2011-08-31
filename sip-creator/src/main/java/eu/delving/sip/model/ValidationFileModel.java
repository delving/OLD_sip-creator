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

package eu.delving.sip.model;

import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.files.FileStoreException;

import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import java.util.List;

/**
 * A list model for showing the contents of the validation file
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ValidationFileModel extends AbstractListModel implements Runnable, MappingModel.Listener {
    private SipModel sipModel;
    private List<String> lines;
    private RecordMapping recordMapping;

    public ValidationFileModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    @Override
    public int getSize() {
        return lines == null ? 0 : lines.size();
    }

    @Override
    public Object getElementAt(int i) {
        return lines.get(i);
    }

    @Override
    public void run() {
        int size = getSize();
        if (size > 0) {
            lines = null;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    fireIntervalRemoved(this, 0, getSize());
                }
            });
        }
        try {
            final List<String> freshLines = sipModel.getStoreModel().getStore().getValidationReport(recordMapping);
            if (freshLines != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        lines = freshLines;
                        fireIntervalAdded(ValidationFileModel.this, 0, getSize());
                    }
                });

            }
        }
        catch (FileStoreException e) {
            sipModel.getUserNotifier().tellUser("Validation Report", e);
        }
    }

    @Override
    public void factChanged() {
    }

    @Override
    public void select(FieldMapping fieldMapping) {
    }

    @Override
    public void selectedChanged() {
    }

    @Override
    public void mappingChanged(RecordMapping recordMapping) {
        this.recordMapping = recordMapping;
        sipModel.execute(this);
    }

    public void kick() {
        mappingChanged(sipModel.getMappingModel().getRecordMapping()); // to fire it off
    }
}
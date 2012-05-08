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

import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;

import javax.swing.*;
import javax.xml.validation.Validator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.sip.files.DataSetState.EMPTY;

/**
 * An observable hole to put the current data set
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetModel implements RecDefModel {
    private DataSet dataSet;
    private DataSetState dataSetState = EMPTY;
    private MappingModel mappingModel = new MappingModel();

    public DataSetModel() {
        new StateCheckTimer();
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public boolean hasDataSet() {
        return dataSet != null;
    }

    public boolean hasPrefix() {
        return mappingModel.hasRecMapping();
    }

    public DataSetState getDataSetState() {
        return hasPrefix() && hasDataSet() ? dataSet.getState(mappingModel.getPrefix()) : EMPTY;
    }

    public RecMapping getRecMapping() throws StorageException {
        if (!hasPrefix()) throw new StorageException("No prefix available");
        return getDataSet().getRecMapping(getPrefix(), this);
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return mappingModel.getPrefix();
    }

    public Validator newValidator() throws MetadataException {
        try {
            return dataSet.newValidator(getPrefix());
        }
        catch (StorageException e) {
            throw new MetadataException("Unable to get validator", e);
        }
    }

    @Override
    public RecDefTree createRecDef(String prefix) throws MetadataException {
        try {
            RecDef recDef = dataSet.getRecDef(prefix);
            return RecDefTree.create(recDef);
        }
        catch (StorageException e) {
            throw new MetadataException(e);
        }
    }

    public void setDataSet(final DataSet dataSet, String prefix) throws StorageException {
        this.dataSet = dataSet;
        if (dataSet != null) {
            mappingModel.setRecMapping(dataSet.getRecMapping(prefix, this));
        }
        else {
            mappingModel.setRecMapping(null);
        }
    }

    public void clearDataSet() {
        dataSet = null;
        mappingModel.setRecMapping(null);
    }

    public boolean deleteValidation() throws StorageException {
        return hasDataSet() && hasPrefix() && dataSet.deleteValidation(getPrefix());
    }

    private class StateCheckTimer implements Runnable, ActionListener {
        private Timer timer = new Timer(1000, this);

        private StateCheckTimer() {
            timer.setRepeats(true);
            timer.start();
        }

        @Override
        public void run() {
            if (hasDataSet()) {
                final DataSetState actualState = getDataSetState();
                if (actualState != dataSetState) {
                    dataSetState = actualState;
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            for (Listener listener : listeners) listener.stateChanged(DataSetModel.this, actualState);
                        }
                    });
                }
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(this);
        }
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {

        void stateChanged(DataSetModel model, DataSetState state);

    }
}

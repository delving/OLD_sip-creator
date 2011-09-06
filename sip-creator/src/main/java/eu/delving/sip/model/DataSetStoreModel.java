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

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the current data set store
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetStoreModel implements MetadataModel {
    private FileStore.DataSetStore dataSetStore;
    private FileStore.StoreState storeState = FileStore.StoreState.EMPTY;
    private List<FactDefinition> factDefinitions = new ArrayList<FactDefinition>();
    private Map<String, RecordDefinition> recordDefinitions = new TreeMap<String,RecordDefinition>();

    public boolean hasStore() {
        return dataSetStore != null;
    }

    public FileStore.DataSetStore getStore() {
        if (dataSetStore == null) {
            throw new IllegalStateException("There is no dataset store in the model!");
        }
        return dataSetStore;
    }

    @Override
    public List<FactDefinition> getFactDefinitions() {
        return null;  // todo: implement
    }

    @Override
    public Set<String> getPrefixes() {
        return recordDefinitions.keySet();
    }

    @Override
    public RecordDefinition getRecordDefinition(String prefix) {
        RecordDefinition def = recordDefinitions.get(prefix);
        if (def == null) {
            throw new RuntimeException("Expected to have a record definition for prefix "+prefix);
        }
        return def;
    }

    public void setStore(final FileStore.DataSetStore dataSetStore) throws FileStoreException {
        this.dataSetStore = dataSetStore;
        this.factDefinitions.clear();
        this.factDefinitions.addAll(dataSetStore.getFactDefinitions());
        this.recordDefinitions.clear();
        for (RecordDefinition recordDefinition : dataSetStore.getRecordDefinitions(factDefinitions)) {
            recordDefinitions.put(recordDefinition.prefix, recordDefinition);
        }
        this.storeState = dataSetStore.getState();
        if (SwingUtilities.isEventDispatchThread()) {
            for (Listener listener : listeners) {
                listener.storeSet(dataSetStore);
            }
        }
        else {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    for (Listener listener : listeners) {
                        listener.storeSet(dataSetStore);
                    }
                }
            });
        }
    }

    // note: doesn't matter which thread calls this
    public void checkState() {
        final FileStore.StoreState actualState = dataSetStore.getState();
        if (actualState != storeState) {
            storeState = actualState;
            if (SwingUtilities.isEventDispatchThread()) {
                for (Listener listener : listeners) {
                    listener.storeStateChanged(dataSetStore, actualState);
                }
            }
            else {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener listener : listeners) {
                            listener.storeStateChanged(dataSetStore, actualState);
                        }
                    }
                });
            }
        }
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void storeSet(FileStore.DataSetStore store);

        void storeStateChanged(FileStore.DataSetStore store, FileStore.StoreState storeState);
    }
}

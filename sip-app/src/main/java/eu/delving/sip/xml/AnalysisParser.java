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

package eu.delving.sip.xml;

import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.stats.Stats;
import org.apache.commons.io.IOUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.InputStream;

/**
 * Analyze xml input and compile statistics. When analysis fails, the .error will be appended to the filename
 * of the erroneous file.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class AnalysisParser implements Work.LongTermWork, Work.DataSetWork {
    public static final int ELEMENT_STEP = 10000;
    private Stats stats = new Stats();
    private Listener listener;
    private DataSetModel dataSetModel;
    private ProgressListener progressListener;

    public interface Listener {

        void success(Stats stats);

        void failure(String message, Exception exception);
    }

    public AnalysisParser(DataSetModel dataSetModel, int maxUniqueValueLength, Listener listener) {
        this.dataSetModel = dataSetModel;
        this.listener = listener;
        stats.maxUniqueValueLength = maxUniqueValueLength;
    }

    @Override
    public Job getJob() {
        return Job.PARSE_ANALYZE;
    }

    @Override
    public DataSet getDataSet() {
        return dataSetModel.getDataSet();
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.setProgressMessage(String.format(
                "Analyzing data of '%s'",
                dataSetModel.getDataSet().getSpec()
        ));
    }

    @Override
    public void run() {
        try {
            XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
            xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            xmlif.configureForSpeed();
            Path path = Path.create();
            boolean running = true;
            InputStream inputStream = null;
            if (dataSetModel.isEmpty()) return;
            try {
                switch (dataSetModel.getDataSetState()) {
                    case IMPORTED:
                        inputStream = dataSetModel.getDataSet().openImportedInputStream();
                        break;
                    case SOURCED:
                        inputStream = dataSetModel.getDataSet().openSourceInputStream();
                        stats.setRecordRoot(Storage.RECORD_ROOT);
                        stats.sourceFormat = true;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state: " + dataSetModel.getDataSetState());
                }
                stats.name = dataSetModel.getDataSet().getDataSetFacts().get("name");
                XMLStreamReader2 input = (XMLStreamReader2) xmlif.createXMLStreamReader(getClass().getName(), inputStream);
                StringBuilder text = new StringBuilder();
                int count = 0;
                while (running) {
                    switch (input.getEventType()) {
                        case XMLEvent.START_ELEMENT:
                            if (++count % ELEMENT_STEP == 0) {
                                if (listener != null) progressListener.setProgress(count);
                            }
                            for (int walk = 0; walk < input.getNamespaceCount(); walk++) {
                                stats.recordNamespace(input.getNamespacePrefix(walk), input.getNamespaceURI(walk));
                            }
                            path = path.child(Tag.element(input.getName()));
                            if (input.getAttributeCount() > 0) {
                                for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                                    QName attributeName = input.getAttributeName(walk);
                                    Path withAttr = path.child(Tag.attribute(attributeName));
                                    stats.recordValue(withAttr, input.getAttributeValue(walk));
                                }
                            }
                            break;
                        case XMLEvent.CHARACTERS:
                        case XMLEvent.CDATA:
                            text.append(input.getText());
                            break;
                        case XMLEvent.END_ELEMENT:
                            stats.recordValue(path, text.toString().trim());
                            text.setLength(0);
                            path = path.parent();
                            break;
                    }
                    if (!input.hasNext()) break;
                    input.next();
                }
            }
            finally {
                IOUtils.closeQuietly(inputStream);
            }
            if (running) {
                stats.finish();
                listener.success(stats);
            }
            else {
                listener.failure(null, null);
            }
        }
        catch (CancelException e) {
            listener.failure("Cancellation", e);
        }
        catch (Exception e) {
            try {
                File renamedTo;
                switch (dataSetModel.getDataSetState()) {
                    case IMPORTED:
                    case DELIMITED:
                        renamedTo = dataSetModel.getDataSet().renameInvalidImport();
                        break;
                    case SOURCED:
                        renamedTo = dataSetModel.getDataSet().renameInvalidSource();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state " + dataSetModel.getDataSetState(), e);
                }
                listener.failure(String.format("The imported file contains errors, the file has been renamed to '%s'", renamedTo.getName()), e);
            }
            catch (StorageException se) {
                listener.failure("Error renaming file", e);
            }
        }
    }
}
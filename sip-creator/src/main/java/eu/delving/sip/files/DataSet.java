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

package eu.delving.sip.files;

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * An individual data set within the Storage
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface DataSet {

    String getSpec();

    String getOrganization();

    String getLatestPrefix();

    RecordMapping setLatestPrefix(String prefix, MetadataModel metadataModel) throws StorageException;

    List<FactDefinition> getFactDefinitions() throws StorageException;

    List<RecordDefinition> getRecordDefinitions(List<FactDefinition> factDefinitions) throws StorageException;

    DataSetState getState();

    Map<String, String> getDataSetFacts();

    Map<String, String> getHints();

    void setHints(Map<String, String> hints) throws StorageException;

    boolean isRecentlyImported();

    void deleteConverted() throws StorageException;

    void deleteValidation(String metadataPrefix) throws StorageException;

    void deleteValidations() throws StorageException;

    File importedOutput() throws StorageException;

    InputStream openImportedInputStream() throws StorageException;

    InputStream openSourceInputStream() throws StorageException;

    File renameInvalidSource() throws StorageException;

    File renameInvalidImport() throws StorageException;

    Statistics getLatestStatistics();

    Statistics getStatistics(boolean sourceFormat);

    void setStatistics(Statistics statistics) throws StorageException;

    RecordMapping getRecordMapping(String prefix, MetadataModel metadataModel) throws StorageException;

    void setRecordMapping(RecordMapping recordMapping) throws StorageException;

    void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws StorageException;

    PrintWriter openReportWriter(RecordMapping recordMapping) throws StorageException;

    List<String> getReport(RecordMapping recordMapping) throws StorageException;

    void externalToImported(File inputFile, ProgressListener progressListener) throws StorageException;

    void importedToSource(ProgressListener progressListener) throws StorageException;

    List<File> getUploadFiles() throws StorageException;

    void fromSipZip(InputStream inputStream, long streamLength, ProgressListener progressListener) throws IOException, StorageException;

    void remove() throws StorageException;

}


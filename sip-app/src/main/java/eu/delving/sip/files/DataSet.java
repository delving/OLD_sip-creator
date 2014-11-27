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

package eu.delving.sip.files;

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.ProgressListener;
import eu.delving.stats.Stats;

import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * An individual data set within the Storage
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface DataSet extends Comparable<DataSet> {

    String getSpec();

    List<SchemaVersion> getSchemaVersions();

    RecDef getRecDef(String prefix) throws StorageException;

    Validator newValidator(String prefix) throws StorageException;

    boolean isProcessed(String prefix) throws StorageException;

    DataSetState getState(String prefix);

    Map<String, String> getDataSetFacts();

    void setDataSetFacts(Map<String, String> dataSetFacts) throws StorageException;

    Map<String, String> getHints();

    void setHints(Map<String, String> hints) throws StorageException;

    boolean deleteTarget(String prefix) throws StorageException;

    InputStream openSourceInputStream() throws StorageException;

    File targetOutput(String prefix);

    Stats getStats();

    void setStats(Stats stats) throws StorageException;

    RecMapping getRecMapping(String prefix, RecDefModel recDefModel) throws StorageException;

    RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) throws StorageException;

    void setRecMapping(RecMapping recMapping, boolean freeze) throws StorageException;

    List<File> getRecMappingFiles(String prefix) throws StorageException;

    ReportWriter openReportWriter(RecDef recDef) throws StorageException;

    ReportFile getReport(String prefix) throws StorageException;

    void deleteSource();

    void fromSipZip(File sipZipFile, ProgressListener progressListener) throws IOException, StorageException;

    File toSipZip(String prefix) throws StorageException;

    void remove() throws StorageException;

}


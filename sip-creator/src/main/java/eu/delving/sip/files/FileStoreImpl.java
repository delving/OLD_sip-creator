/*
 * Copyright 2010 DELVING BV
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

import eu.delving.metadata.Facts;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreImpl extends FileStoreBase implements FileStore {

    private File home;
    private MetadataModel metadataModel;

    public FileStoreImpl(File home, MetadataModel metadataModel) throws FileStoreException {
        this.home = home;
        this.metadataModel = metadataModel;
        if (!home.exists()) {
            if (!home.mkdirs()) {
                throw new FileStoreException(String.format("Unable to create file store in %s", home.getAbsolutePath()));
            }
        }
    }

    @Override
    public void setTemplate(String name, RecordMapping recordMapping) throws FileStoreException {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        try {
            FileOutputStream fos = new FileOutputStream(templateFile);
            RecordMapping.write(recordMapping, fos);
            fos.close();
        }
        catch (IOException e) {
            throw new FileStoreException(String.format("Unable to save template to %s", templateFile.getAbsolutePath()), e);
        }
    }

    @Override
    public Map<String, RecordMapping> getTemplates() throws FileStoreException {
        Map<String, RecordMapping> templates = new TreeMap<String, RecordMapping>();
        for (File templateFile : home.listFiles(new MappingFileFilter())) {
            try {
                FileInputStream fis = new FileInputStream(templateFile);
                RecordMapping recordMapping = RecordMapping.read(fis, metadataModel);
                fis.close();
                String name = templateFile.getName();
                name = name.substring(MAPPING_FILE_PREFIX.length());
                name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
                templates.put(name, recordMapping);
            }
            catch (Exception e) {
                delete(templateFile);
            }
        }
        return templates;
    }

    @Override
    public void deleteTemplate(String name) throws FileStoreException {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        delete(templateFile);
    }

    @Override
    public Map<String, DataSetStore> getDataSetStores() {
        Map<String, DataSetStore> map = new TreeMap<String, DataSetStore>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    map.put(file.getName(), new DataSetStoreImpl(file));
                }
            }
        }
        return map;
    }

    @Override
    public DataSetStore createDataSetStore(String spec) throws FileStoreException {
        File directory = new File(home, spec);
        if (directory.exists()) {
            throw new FileStoreException(String.format("Data store directory %s already exists", directory.getAbsolutePath()));
        }
        if (!directory.mkdirs()) {
            throw new FileStoreException(String.format("Unable to create data store directory %s", directory.getAbsolutePath()));
        }
        return new DataSetStoreImpl(directory);
    }

    public class DataSetStoreImpl implements DataSetStore, Serializable {

        private File directory;

        public DataSetStoreImpl(File directory) {
            this.directory = directory;
        }

        @Override
        public String getSpec() {
            return directory.getName();
        }

        @Override
        public Facts getFacts() {
            File factsFile = findFactsFile(directory);
            Facts facts = null;
            if (factsFile.exists()) {
                try {
                    facts = Facts.read(new FileInputStream(factsFile));
                }
                catch (Exception e) {
                    // eat this exception
                }
            }
            if (facts == null) {
                facts = new Facts();
            }
            return facts;
        }

        @Override
        public InputStream getImportedInputStream() throws FileStoreException {
            File imported = findImportedFile(directory);
            try {
                return new GZIPInputStream(new FileInputStream(imported));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", imported.getAbsolutePath()), e);
            }
        }


        @Override
        public InputStream getSourceInputStream() throws FileStoreException {
            File source = findSourceFile(directory);
            try {
                return new GZIPInputStream(new FileInputStream(source));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", source.getAbsolutePath()), e);
            }
        }

        @Override
        public List<FieldStatistics> getStatistics() {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            if (statisticsFile.exists()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(statisticsFile)));
                    @SuppressWarnings("unchecked")
                    List<FieldStatistics> fieldStatisticsList = (List<FieldStatistics>) in.readObject();
                    in.close();
                    return fieldStatisticsList;
                }
                catch (Exception e) {
                    try {
                        delete(statisticsFile);
                    }
                    catch (FileStoreException e1) {
                        // give up
                    }
                }
            }
            return null;
        }

        @Override
        public void setStatistics(List<FieldStatistics> fieldStatisticsList) throws FileStoreException {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            try {
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(fieldStatisticsList);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException {
            RecordDefinition recordDefinition = metadataModel.getRecordDefinition(metadataPrefix);
            File mappingFile = findMappingFile(directory, metadataPrefix);
            if (mappingFile.exists()) {
                try {
                    FileInputStream is = new FileInputStream(mappingFile);
                    return RecordMapping.read(is, metadataModel);
                }
                catch (Exception e) {
                    throw new FileStoreException(String.format("Unable to read mapping from %s", mappingFile.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(recordDefinition.prefix);
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws FileStoreException {
            File mappingFile = new File(directory, String.format(MAPPING_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                FileOutputStream out = new FileOutputStream(mappingFile);
                RecordMapping.write(recordMapping, out);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save mapping to %s", mappingFile.getAbsolutePath()), e);
            }
        }

        @Override
        public void remove() throws FileStoreException {
            delete(directory);
        }

        @Override
        public List<File> getUploadFiles() throws FileStoreException {
            try {
                List<File> files = new ArrayList<File>();
//                files.add(Hasher.ensureFileHashed(findParseHintsFile(directory));
                files.add(Hasher.ensureFileHashed(findSourceFile(directory)));
                for (File file : findMappingFiles(directory)) {
                    files.add(Hasher.ensureFileHashed(file));
                }
                return files;
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to collect upload files", e);
            }
        }

        @Override
        public File getImportedFile() {
            return findImportedFile(directory);
        }

        public File getDiscardedFile(RecordMapping recordMapping) {
            return new File(directory, String.format(DISCARDED_FILE_PATTERN, recordMapping.getPrefix()));
        }

        @Override
        public void importSource(File inputFile, ProgressListener progressListener) throws FileStoreException {
            int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
            if (progressListener != null) progressListener.prepareFor(fileBlocks);
            File source = new File(directory, IMPORTED_FILE_NAME);
            Hasher hasher = new Hasher();
            boolean cancelled = false;
            try {
                InputStream inputStream;
                if (inputFile.getName().endsWith(".xml")) {
                    inputStream = new FileInputStream(inputFile);
                }
                else if (inputFile.getName().endsWith(".xml.gz")) {
                    inputStream = new GZIPInputStream(new FileInputStream(inputFile));
                }
                else {
                    throw new IllegalArgumentException("Input file should be .xml or .xml.gz, but it is " + inputFile.getName());
                }
                OutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(source));
                byte[] buffer = new byte[BLOCK_SIZE];
                long totalBytesRead = 0;
                int bytesRead;
                while (-1 != (bytesRead = inputStream.read(buffer))) {
                    gzipOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (totalBytesRead / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(buffer, bytesRead);
                }
                if (progressListener != null) progressListener.finished(!cancelled);
                inputStream.close();
                gzipOutputStream.close();
            }
            catch (Exception e) {
                if (progressListener != null) progressListener.finished(false);
                throw new FileStoreException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            if (cancelled) {
                delete(source);
            }
            else {
                File hashedSource = new File(directory, hasher.prefixFileName(IMPORTED_FILE_NAME));
                if (hashedSource.exists()) {
                    delete(source);
                    throw new FileStoreException("This import was identical to the previous one. Discarded.");
                }
                if (!source.renameTo(hashedSource)) {
                    throw new FileStoreException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
                File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
                delete(statisticsFile);
            }
        }

        @Override
        public void convertSource(ProgressListener progressListener) throws FileStoreException {
            // todo: get the record root (assuming it has been set)
            // todo: get the unique id path (minus the root is the unique id)
            // todo: take IMPORTED_FILE_NAME, and create standard sip-creator source format file
            // todo: include in the source: record root path and unique id path
        }

        @Override
        public void acceptSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws FileStoreException {
            ZipEntry zipEntry;
            byte[] buffer = new byte[BLOCK_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            boolean cancelled = false;
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    File file = new File(directory, fileName);
                    if (fileName.equals(SOURCE_FILE_NAME)) {
                        Hasher hasher = new Hasher();
                        GZIPInputStream gzipInputStream = new GZIPInputStream(zipInputStream);
                        GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(file));
                        while (!cancelled && -1 != (bytesRead = gzipInputStream.read(buffer))) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            if (progressListener != null) {
                                if (!progressListener.setProgress((int) (totalBytesRead / BLOCK_SIZE))) {
                                    cancelled = true;
                                    break;
                                }
                            }
                            hasher.update(buffer, bytesRead);
                        }
                        if (progressListener != null) progressListener.finished(!cancelled);
                        outputStream.close();
                        File hashedSource = new File(directory, hasher.prefixFileName(SOURCE_FILE_NAME));
                        if (!file.renameTo(hashedSource)) {
                            throw new FileStoreException(String.format("Unable to rename %s to %s", file.getAbsolutePath(), hashedSource.getAbsolutePath()));
                        }
                    }
                    else {
                        IOUtils.copy(zipInputStream, new FileOutputStream(file));
                        Hasher.ensureFileHashed(file);
                    }
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public String toString() {
            return getSpec();
        }
    }
}

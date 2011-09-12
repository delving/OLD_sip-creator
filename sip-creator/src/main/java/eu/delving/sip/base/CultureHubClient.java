/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.metadata.Hasher;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CultureHubClient {
    private static final int BLOCK_SIZE = 4096;
    private Logger log = Logger.getLogger(getClass());
    private Context context;
    private HttpClient httpClient = new DefaultHttpClient();


    public interface Context {
        String getServerUrl();

        String getAccessToken() throws ClientException;

        void invalidateTokens();

        void dataSetCreated(DataSet dataSet);

        void tellUser(String message);

    }

    public interface ListReceiveNotifier {

        /**
         * Successfully received lists from server.
         *
         * @param entries The entry list.
         */
        void listReceived(List<DataSetEntry> entries);

        /**
         * Error fetching list.
         *
         * @param e What went wrong?
         */
        void failed(Exception e);
    }

    public enum Code {
        OK(SC_OK),
        UNAUTHORIZED(SC_UNAUTHORIZED),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR),
        UNKNOWN_RESPONSE(-1);

        private int httpCode;

        Code(int httpCode) {
            this.httpCode = httpCode;
        }

        static Code from(HttpResponse httpResponse) {
            int httpCode = httpResponse.getStatusLine().getStatusCode();
            for (Code code : values()) {
                if (code.httpCode == httpCode) {
                    return code;
                }
            }
            return UNKNOWN_RESPONSE;
        }
    }

    public CultureHubClient(Context context) {
        this.context = context;
    }

    public void fetchDataSetList(ListReceiveNotifier listReceiveNotifier) {
        Exec.work(new ListFetcher(listReceiveNotifier));
    }

    public void downloadDataSet(DataSet dataSet, ProgressListener progressListener) {
        Exec.work(new DataSetDownloader(dataSet, progressListener));
    }

    public void uploadFiles(DataSet dataSet, ProgressListener progressListener) throws StorageException {
        Exec.work(new FileUploader(dataSet, progressListener));
    }

    private class ListFetcher implements Runnable {
        private ListReceiveNotifier listReceiveNotifier;

        public ListFetcher(ListReceiveNotifier listReceiveNotifier) {
            this.listReceiveNotifier = listReceiveNotifier;
        }

        @Override
        public void run() {
            HttpEntity entity = null;
            try {
                String url = String.format(
                        "%s/list?accessKey=%s",
                        context.getServerUrl(),
                        context.getAccessToken()
                );
                log.info("requesting list: " + url);
                HttpGet get = new HttpGet(url);
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                switch (Code.from(response)) {
                    case OK:
                        entity = response.getEntity();
                        DataSetList dataSetList = (DataSetList) listStream().fromXML(entity.getContent());
                        log.info("list received:\n" + dataSetList);
                        listReceiveNotifier.listReceived(dataSetList.list);
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        log.error("Authorization failure, please try again");
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        log.warn("Unable to fetch data set list. HTTP response " + response.getStatusLine().getReasonPhrase());
                        context.tellUser(String.format("Error fetching list from server:<br><br>%s", response.getStatusLine().getReasonPhrase()));
                        break;
                }
            }
            catch (Exception e) {
                log.error("Unable to download source", e);
                context.tellUser(String.format("Error fetching list from server:<br><br>%s", e.getMessage()));
                listReceiveNotifier.failed(e);
            }
            finally {
                if (null != entity) {
                    try {
                        entity.consumeContent();
                    }
                    catch (IOException e) {
                        log.warn(String.format("Error consuming entity: %s", e.getMessage()));
                    }
                }
            }
        }
    }

    private class DataSetDownloader implements Runnable {
        private DataSet dataSet;
        private ProgressListener progressListener;

        private DataSetDownloader(DataSet dataSet, ProgressListener progressListener) {
            this.dataSet = dataSet;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = new HttpGet(String.format(
                        "%s/fetch/%s-sip.zip?accessKey=%s",
                        context.getServerUrl(),
                        dataSet.getSpec(),
                        context.getAccessToken()
                ));
                get.setHeader("Accept", "application/zip");
                HttpResponse response = httpClient.execute(get);
                switch (Code.from(response)) {
                    case OK:
                        HttpEntity entity = response.getEntity();
                        dataSet.fromSipZip(entity.getContent(), entity.getContentLength(), progressListener);
                        success = true;
                        context.dataSetCreated(dataSet);
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        log.warn("Unable to download source. HTTP response " + response.getStatusLine().getReasonPhrase());
                        context.tellUser("Unable to download data set"); // todo: tell them why
                        break;
                }
            }
            catch (Exception e) {
                log.warn("Unable to download data set", e);
                context.tellUser("Unable to download data set"); // todo: tell them why
            }
            finally {
                if (!success) {
                    try {
                        dataSet.remove();
                    }
                    catch (StorageException e1) {
                        context.tellUser("Unable to remove local data set");
                    }
                }
                progressListener.finished(success);
            }
        }
    }

    public class FileUploader implements Runnable {
        private DataSet dataSet;
        private List<File> uploadFiles;
        private ProgressListener progressListener;

        public FileUploader(DataSet dataSet, ProgressListener progressListener) throws StorageException {
            this.dataSet = dataSet;
            this.uploadFiles = dataSet.getUploadFiles();
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            try {
                HttpPost post = new HttpPost(createListRequestUrl());
                post.setEntity(createListEntity());
                post.setHeader("Accept", "text/plain");
                HttpResponse response = httpClient.execute(post);
                switch (Code.from(response)) {
                    case OK:
                        HttpEntity entity = response.getEntity();
                        String listString = EntityUtils.toString(entity);
                        entity.consumeContent();
                        List<File> filteredUploadFiles = new ArrayList<File>();
                        for (String fileName : listString.split("\n")) {
                            for (File file : uploadFiles) {
                                if (file.getName().equals(fileName)) {
                                    filteredUploadFiles.add(file);
                                }
                            }
                        }
                        uploadFiles = filteredUploadFiles;
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        throw new IOException("Unable to fetch file list, response: " + Code.from(response));
                }
                for (File file : uploadFiles) {
                    log.info("Uploading " + file);
                    post = new HttpPost(createFileRequestUrl(file));
                    post.setEntity(new FileEntity(file, progressListener));
                    response = httpClient.execute(post);
                    response.getEntity().consumeContent();
                    switch (Code.from(response)) {
                        case OK:
                            break;
                        case UNAUTHORIZED:
                            context.invalidateTokens();
                            notifyUser("Authorization failure, please try again");
                            break;
                        case SYSTEM_ERROR:
                        case UNKNOWN_RESPONSE:
                            throw new IOException(String.format("Unable to upload file %s, response: %s", file.getName(), Code.from(response)));
                    }
                }
            }
            catch (IOException e) {
                log.error("Error while connecting", e);
                notifyUser(e.getMessage());
            }
            catch (ClientException e) {
                log.error("Error ");
                notifyUser(e.getMessage());
            }
        }

        private HttpEntity createListEntity() throws UnsupportedEncodingException {
            StringBuilder fileList = new StringBuilder();
            for (File file : uploadFiles) {
                fileList.append(file.getName()).append("\n");
            }
            return new StringEntity(fileList.toString());
        }

        private String createListRequestUrl() throws ClientException {
            return String.format(
                    "%s/submit/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    context.getAccessToken()
            );
        }

        private String createFileRequestUrl(File file) throws ClientException {
            return String.format(
                    "%s/submit/%s/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    file.getName(),
                    context.getAccessToken()
            );
        }
    }

    private static String deriveContentType(File file) {
        String name = Hasher.extractFileName(file);
        if (name.endsWith(".gz")) {
            return "application/x-gzip";
        }
        else if (name.endsWith(".txt")) {
            return "text/plain";
        }
        else if (name.endsWith(".xml")) {
            return "text/xml";
        }
        else if (name.endsWith(".int")) {
            return "application/octet-stream";
        }
        else {
            throw new RuntimeException("Cannot determine content type of " + file.getAbsolutePath());
        }
    }

    private void notifyUser(final String message) {
        log.warn("Problem communicating with CultureHub: " + message);
        context.tellUser("<html>Sorry, there was a problem communicating with Repository<br>" + message);
    }

    private static class FileEntity extends AbstractHttpEntity implements Cloneable {
        private final File file;
        private final ProgressListener progressListener;
        private long bytesSent;
        private int blocksReported;
        private boolean abort = false;

        public FileEntity(File file, ProgressListener progressListener) {
            this.file = file;
            this.progressListener = progressListener;
            setChunked(true);
            setContentType(deriveContentType(file));
        }

        public boolean isRepeatable() {
            return true;
        }

        public long getContentLength() {
            return this.file.length();
        }

        public InputStream getContent() throws IOException {
            return new FileInputStream(this.file);
        }

        public void writeTo(OutputStream outputStream) throws IOException {
            progressListener.prepareFor((int) (getContentLength() / BLOCK_SIZE));
            InputStream inputStream = new FileInputStream(this.file);
            try {
                byte[] buffer = new byte[BLOCK_SIZE];
                int bytes;
                while (!abort && (bytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytes);
                    bytesSent += bytes;
                    int blocks = (int) (bytesSent / BLOCK_SIZE);
                    if (blocks > blocksReported) {
                        blocksReported = blocks;
                        if (!progressListener.setProgress(blocksReported)) {
                            abort = true;
                        }
                    }
                }
                outputStream.flush();
            }
            finally {
                inputStream.close();
                progressListener.finished(!abort);
            }
        }

        public boolean isStreaming() {
            return false;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    private XStream listStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(DataSetList.class);
        return stream;
    }

    @XStreamAlias("data-set-list")
    public static class DataSetList {
        @XStreamImplicit
        List<DataSetEntry> list;

        public String toString() {
            StringBuilder out = new StringBuilder("data-set-list");
            if (list == null || list.isEmpty()) {
                out.append(" (empty)");
            }
            else {
                out.append('\n');
                for (DataSetEntry entry : list) {
                    out.append('\t');
                    out.append(entry);
                    out.append('\n');
                }
            }
            return out.toString();
        }
    }

    @XStreamAlias("data-set")
    public static class DataSetEntry {
        public String spec;
        public String name;
        public String state;
        public int recordCount;
        public Ownership ownership;
        public LockedBy lockedBy;

        public String toString() {
            return "data-set spec=" + spec;
        }
    }

    @XStreamAlias("ownership")
    public static class Ownership {
        public String username;
        public String fullname;
        public String email;
    }

    @XStreamAlias("lockedBy")
    public static class LockedBy {
        public String username;
        public String fullname;
        public String email;
    }
}

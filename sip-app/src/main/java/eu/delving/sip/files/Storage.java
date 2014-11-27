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

import eu.delving.metadata.Path;

import java.io.File;
import java.util.Map;

/**
 * This interface describes how files are maintained by the SIP-Creator
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Storage {

    File cache(String fileName);

    Map<String, DataSet> getDataSets();

    DataSet createDataSet(String spec) throws StorageException;

    enum FileType {
        SOURCE("source.xml.gz", null, null, null, 2),
        SOURCE_STATS("stats-source.xml.gz"),
        FACTS("narthex_facts.txt"),
        HINTS("hints.txt"),
        MAPPING(null, "mapping_", ".xml", "mapping_%s.xml", 30),
        REPORT(null, "report_", null, "report_%s.txt", 1),
        REPORT_INDEX(null, "report_", null, "report_%s.long", 1),
        LINKS(null, "links_", null, "links_%s.csv.gz", 1);

        private String name, prefix, suffix, pattern;
        private int historySize = 1;

        FileType(String name, String prefix, String suffix, String pattern, int historySize) {
            this.name = name;
            this.prefix = prefix;
            this.suffix = suffix;
            this.pattern = pattern;
            this.historySize = historySize;
        }

        FileType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getName(String sub) {
            if (pattern == null) throw new RuntimeException("No pattern");
            return String.format(pattern, sub);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public int getHistorySize() {
            return historySize;
        }
    }

    String FACTS_TAG = "facts";
    String CONSTANT_TAG = "constant";
    String RECORD_ROOT_PATH = "recordRootPath";
    String RECORD_COUNT = "recordCount";
    String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
    String MAX_UNIQUE_VALUE_LENGTH = "maxUniqueValueLength";
    String UNIQUE_VALUE_CONVERTER = "uniqueValueConverter";
    String SCHEMA_VERSIONS = "schemaVersions";
    String HARVEST_URL = "harvestUrl";
    String NARTHEX_URL = "narthexUrl";
    String NARTHEX_API_KEY = "narthexApiKey";
    String NARTHEX_DATASET_NAME = "narthexDatasetName";
    String NARTHEX_PREFIX = "narthexPrefix";
    String CACHE_DIR = "__cache__";
    String SIP_ZIPS_DIR = "SipZips";
    String FRAME_ARRANGEMENTS_FILE = "frame-arrangements.xml";

    String SOURCE_ROOT_TAG = "pockets";
    String SOURCE_RECORD_TAG = "pocket";
    String UNIQUE_ATTR = "id";
    Path RECORD_CONTAINER = Path.create(String.format("/%s/%s", SOURCE_ROOT_TAG, SOURCE_RECORD_TAG));
    Path UNIQUE_ELEMENT = Path.create(String.format("/%s/%s/@%s", SOURCE_ROOT_TAG, SOURCE_RECORD_TAG, UNIQUE_ATTR));
    long MAPPING_FREEZE_INTERVAL = 60000;
}

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

package eu.delving.metadata;

import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;

import java.util.ArrayList;
import java.util.List;

/**
* Part of the record definition
*
* @author Gerald de Jong <gerald@delving.eu>
*/

@XStreamAlias("opt-list")
public class OptList {

    @XStreamAsAttribute
    public String displayName;

    @XStreamAsAttribute
    public boolean dictionary;

    @XStreamAsAttribute
    public Path path;

    @XStreamAsAttribute
    public Tag key;

    @XStreamAsAttribute
    public Tag value;

    @XStreamAsAttribute
    public Tag schema;

    @XStreamAsAttribute
    public Tag schemaUri;

    @XStreamImplicit
    public List<Opt> opts;

    public void resolve(RecDef recDef) {
        for (Opt opt : opts) opt.parent = this;
        if (path == null) throw new RuntimeException("No path for OptList: " + opts);
        if (path.peek().isAttribute()) throw new RuntimeException("An option list may not be connected to an attribute: " + path);
        path = path.withDefaultPrefix(recDef.prefix);
        key = resolveTag(key, recDef);
        value = resolveTag(value, recDef);
        schema = resolveTag(schema, recDef);
        schemaUri = resolveTag(schemaUri, recDef);
        RecDef.Elem elem = recDef.findElem(path);
        elem.optList = this;
    }

    private static Tag resolveTag(Tag tag, RecDef recDef) {
        return tag != null ? tag.defaultPrefix(recDef.prefix) : null;
    }

    public List<String> getValues() {
        List<String> values = new ArrayList<String>(opts.size());
        for (Opt opt : opts) values.add(opt.value);
        return values;
    }

    @XStreamAlias("opt")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"value"})
    public static class Opt {

        @XStreamAsAttribute
        public String key;

        public String value;

        @XStreamAsAttribute
        public String schema;

        @XStreamAsAttribute
        public String schemaUri;

        @XStreamAsAttribute
        public boolean hidden;

        @XStreamOmitField
        public OptList parent;

        public String toString() {
            return String.format("%s: %s", key, value);
        }
    }
}
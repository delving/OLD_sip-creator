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

package eu.delving.schema.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import eu.delving.schema.xml.Schema;
import eu.delving.schema.xml.SchemaFile;
import eu.delving.schema.xml.Schemas;
import eu.delving.schema.xml.Version;

import java.util.Collection;

/**
 * Produce an xstream for the repo
 *
 *
 */

public class XStreamFactory {
    public static XStream getSchemasStream() {
        XStream xstream = new XStream(new PureJavaReflectionProvider());
        // See http://x-stream.github.io/security.html#example
        XStream.setupDefaultSecurity(xstream);
        xstream.setMode(XStream.NO_REFERENCES);

        xstream.addPermission(NullPermission.NULL);
        xstream.addPermission(PrimitiveTypePermission.PRIMITIVES);

        xstream.allowTypeHierarchy(Schemas.class);
        xstream.allowTypeHierarchy(Schema.class);
        xstream.allowTypeHierarchy(Version.class);
        xstream.allowTypeHierarchy(SchemaFile.class);
        xstream.allowTypeHierarchy(String.class);
        xstream.allowTypeHierarchy(Collection.class);

        xstream.processAnnotations(Schemas.class);
        return xstream;
    }
}

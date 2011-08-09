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

package eu.delving.metadata;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.List;

/**
 * Defines the rendering block of the record definition
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("views")
public class ViewDefinition {

    @XStreamImplicit
    public List<View> views;

    @XStreamAlias("view")
    public static class View {

        @XStreamAsAttribute
        public String name;

        @XStreamImplicit
        public List<ViewField> viewFields;
    }

    @XStreamAlias("view-field")
    public static class ViewField {

        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String prefix;


        @XStreamAsAttribute
        public String localName;

        @XStreamImplicit
        public List<FieldRef> fieldRefs;
    }

    @XStreamAlias("field-ref")
    public static class FieldRef {

        @XStreamAsAttribute
        public String prefix;


        @XStreamAsAttribute
        public String localName;

    }

}

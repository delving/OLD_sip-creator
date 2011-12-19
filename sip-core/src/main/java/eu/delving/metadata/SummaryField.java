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

package eu.delving.metadata;

/**
 * The fields that will be considered common to all record definitions are marked by this
 *
 *
 * The following summary fields cannot be said in the Record Definition TYPE, HUB_ID, VISIBILITY, OWNER.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum SummaryField {

    CREATOR,
    TITLE,
    DESCRIPTION,
    SNIPPET,
    THUMBNAIL,
    LANDING_PAGE("landingPage"),
    DEEP_ZOOM_URL("deepZoomUrl"),
    TYPE("recordType"),
    HUB_ID("hubId"),
    VISIBILITY,
    OWNER;

    private static final String NS = "delving_";

    public final String tag;

    SummaryField() {
        this.tag = NS + name().toLowerCase();
    }

    SummaryField(String tag) {
        this.tag = NS + tag;
    }
}

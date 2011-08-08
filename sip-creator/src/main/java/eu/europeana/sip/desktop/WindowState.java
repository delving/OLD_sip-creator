/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.europeana.sip.desktop;

import eu.europeana.sip.desktop.windows.DesktopWindow;

import java.awt.*;
import java.io.Serializable;

/**
 * Store the state of a DesktopWindow.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class WindowState implements Serializable {

    private DesktopWindow.WindowId windowId;
    private Dimension size;
    private Point point;
    private boolean selected;

    public WindowState(DesktopWindow window) {
        this.point = window.getLocation();
        this.windowId = window.getId();
        this.size = window.getSize();
        this.selected = window.isSelected();
    }

    public Point getPoint() {
        return point;
    }

    public DesktopWindow.WindowId getWindowId() {
        return windowId;
    }

    public Dimension getSize() {
        return size;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("WindowState");
        sb.append("{windowId=").append(windowId);
        sb.append(", size=").append(size);
        sb.append(", point=").append(point);
        sb.append(", selected=").append(selected);
        sb.append('}');
        return sb.toString();
    }
}

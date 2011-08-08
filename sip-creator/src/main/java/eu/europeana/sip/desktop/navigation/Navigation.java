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

package eu.europeana.sip.desktop.navigation;

import eu.europeana.sip.desktop.windows.DesktopWindow;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * The navigation bar on the left side of the screen which controls the actions on a data set.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class Navigation extends JPanel {

    private static final Dimension DEFAULT_BUTTON_SIZE = new Dimension(200, 80);
    private static final Logger LOG = Logger.getRootLogger();
    private Actions actions;

    public Navigation(Actions actions) {
        this.actions = actions;
        JPanel vertical = new JPanel();
        vertical.setLayout(new GridLayout(8, 0));
        for (Map.Entry<DesktopWindow.WindowId, AbstractAction> entry : actions.getBarActions().entrySet()) {
            vertical.add(new JButton(entry.getValue()));
        }
        add(vertical);
    }
}


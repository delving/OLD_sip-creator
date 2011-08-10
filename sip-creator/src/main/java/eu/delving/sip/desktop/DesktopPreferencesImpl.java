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

package eu.delving.sip.desktop;


import eu.delving.sip.FileStore;
import eu.delving.sip.desktop.windows.DesktopManager;
import eu.delving.sip.desktop.windows.DesktopWindow;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Uses java.util.prefs.Preferences to access local data. When stored data is found, the listener will be
 * notified with the found data.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopPreferencesImpl implements DesktopPreferences {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String WINDOW_STATE = "windowState";
    private static final String DATA_SET = "dataSet";
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Listener listener;
    private DesktopManager desktopManager;

    public DesktopPreferencesImpl(Listener listener, DesktopManager desktopManager) {
        this.listener = listener;
        this.desktopManager = desktopManager;
        loadCredentials();
        loadWindowState();
        loadDataSet();
    }

    @SuppressWarnings("unchecked")
    private void loadWindowState() {
        byte[] preferences = this.preferences.getByteArray(WINDOW_STATE, null);
        if (null == preferences) {
            return;
        }
        LOG.info("Desktop state found");
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(preferences);
        final List<DesktopWindow> windows = new ArrayList<DesktopWindow>();
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            List<WindowState> windowStates = (List<WindowState>) objectInputStream.readObject();
            for (WindowState windowState : windowStates) {
                DesktopWindow window = desktopManager.getWindow(windowState.getWindowId());
                window.setWindowState(windowState);
                windows.add(window);
            }
            listener.desktopStateFound(
                    new DesktopState() {

                        @Override
                        public List<DesktopWindow> getWindows() {
                            return windows;
                        }
                    }
            );
        }
        catch (IOException e) {
            LOG.error("Error reading desktop state");
        }
        catch (ClassNotFoundException e) {
            LOG.error("Error reading class", e);
        }

    }

    private void loadCredentials() {
        LOG.info("Looking for stored credentials.");
        final String username = preferences.get(USERNAME, null);
        final String password = preferences.get(PASSWORD, null);
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            LOG.info("Found credentials, will notify the listener.");
            listener.credentialsFound(
                    new Credentials() {

                        @Override
                        public String getUsername() {
                            return username;
                        }

                        @Override
                        public String getPassword() {
                            return password;
                        }
                    }
            );
            return;
        }
        LOG.info("No credentials found.");
    }

    private void loadDataSet() {
        byte[] preferences = this.preferences.getByteArray(DATA_SET, null);
        if (null == preferences) {
            LOG.info("No data set found");
            return;
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(preferences);
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            FileStore.DataSetStore dataSet = (FileStore.DataSetStore) objectInputStream.readObject();
            listener.dataSetFound(dataSet);
        }
        catch (IOException e) {
            LOG.error("Error reading data set", e);
        }
        catch (ClassNotFoundException e) {
            LOG.error("Class not found", e);
        }
        catch (ClassCastException e) {
            LOG.error("Error casting class", e);
        }
    }

    @Override
    public void saveCredentials(Credentials credentials) {
        preferences.put(USERNAME, credentials.getUsername());
        preferences.put(PASSWORD, credentials.getPassword());
    }

    @Override
    public void saveDesktopState(DesktopState desktopState) {
        List<WindowState> windowStates = new ArrayList<WindowState>();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (DesktopWindow window : desktopState.getWindows()) {
            if (window.isPreferencesTransient()) {
                LOG.info("Skipping transient window : " + window.getId());
                continue;
            }
            WindowState windowState = new WindowState(window);
            windowStates.add(windowState);
        }
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(windowStates);
            LOG.info(String.format("Saving window state for %s", windowStates));
            preferences.putByteArray(WINDOW_STATE, byteArrayOutputStream.toByteArray());
        }
        catch (IOException e) {
            LOG.error("Error storing desktop state", e);
        }
    }

    @Override
    public void saveDataSet(FileStore.DataSetStore dataSet) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(dataSet);
            LOG.info("Storing data set : " + dataSet);
            preferences.putByteArray(DATA_SET, byteArrayOutputStream.toByteArray());
        }
        catch (IOException e) {
            LOG.error("Error storing data set", e);
        }
    }
}


/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.actions.CreateSipZipAction;
import eu.delving.sip.actions.SelectAnotherMappingAction;
import eu.delving.sip.actions.UnlockMappingAction;
import eu.delving.sip.actions.ValidateAction;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.VisualFeedback;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.SchemaFetcher;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.frames.LogFrame;
import eu.delving.sip.frames.RemoteDataSetFrame;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.StatusPanel;
import eu.delving.sip.panels.WorkPanel;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.base.KeystrokeHelper.MENU_W;
import static eu.delving.sip.base.KeystrokeHelper.attachAccelerator;
import static eu.delving.sip.files.DataSetState.*;

/**
 * The main application, based on the SipModel and bringing everything together in a big frame with a central
 * desktop pane, with a side panel and panels along the bottom, as well as menus above.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Application {
    private static final int DEFAULT_RESIZE_INTERVAL = 1000;
    private static final Dimension MINIMUM_DESKTOP_SIZE = new Dimension(800, 600);
    private static String version;
    private SipModel sipModel;
    private Action validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private StatusPanel statusPanel;
    private Timer resizeTimer;
    private ExpertMenu expertMenu;
    private CreateSipZipAction createSipZipAction;
    private UnlockMappingAction unlockMappingAction;
    private SelectAnotherMappingAction selectAnotherMappingAction;

    private Application(final File storageDir) throws StorageException {
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
            }
        };
        desktop.setMinimumSize(new Dimension(MINIMUM_DESKTOP_SIZE));
        resizeTimer = new Timer(DEFAULT_RESIZE_INTERVAL, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                resizeTimer.stop();
                for (JInternalFrame frame : desktop.getAllFrames()) {
                    if (frame instanceof FrameBase) {
                        ((FrameBase) frame).ensureOnScreen();
                    }
                }
            }
        });
        desktop.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                resizeTimer.restart();
            }
        });
        Preferences preferences = Preferences.userNodeForPackage(SipModel.class);
        feedback = new VisualFeedback(home, desktop, preferences);
        // todo: be sure to set this
        String serverUrl = preferences.get("serverUrl", "http://localhost:9000/narthex");
        HttpClient httpClient = createHttpClient(serverUrl);
        SchemaRepository schemaRepository;
        try {
            schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
        }
        catch (IOException e) {
            throw new StorageException("Unable to create Schema Repository", e);
        }
        ResolverContext context = new ResolverContext();
        Storage storage = new StorageImpl(storageDir, schemaRepository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);
        sipModel = new SipModel(desktop, storage, groovyCodeResource, feedback, preferences);
        NetworkClient networkClient = new NetworkClient(sipModel, httpClient, serverUrl);
        createSipZipAction = new CreateSipZipAction(sipModel);
        expertMenu = new ExpertMenu(desktop, sipModel, networkClient, allFrames);
        statusPanel = new StatusPanel(sipModel);
        home = new JFrame(titleString());
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.getViewSelector().refreshView();
            }
        });
        JPanel content = (JPanel) home.getContentPane();
        content.setFocusable(true);
        FrameBase dataSetFrame = new RemoteDataSetFrame(sipModel, networkClient);
        LogFrame logFrame = new LogFrame(sipModel);
        feedback.setLog(logFrame.getLog());
        allFrames = new AllFrames(sipModel, content, dataSetFrame, logFrame);
        desktop.setBackground(new Color(190, 190, 200));
        content.add(desktop, BorderLayout.CENTER);
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, final boolean locked) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        unlockMappingAction.setEnabled(locked);
                    }
                });
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
            }
        });
        validateAction = new ValidateAction(sipModel, allFrames.prepareForInvestigation(desktop));
        unlockMappingAction = new UnlockMappingAction(sipModel);
        attachAccelerator(unlockMappingAction, home);
        selectAnotherMappingAction = new SelectAnotherMappingAction(sipModel);
        attachAccelerator(selectAnotherMappingAction, home);
        content.add(createStatePanel(), BorderLayout.SOUTH);
        content.add(allFrames.getSidePanel(), BorderLayout.WEST);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        screen.height -= 30;
        home.setSize(screen);
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));
        home.setIconImage(logo.getImage());
        home.setJMenuBar(createMenuBar());
        home.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                quit();
            }
        });
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                statusPanel.setState(state);
                switch (state) {
                    case ABSENT:
                        sipModel.exec(new Work() {
                            @Override
                            public void run() {
                                sipModel.getDataSetFacts().set(null);
                                sipModel.getStatsModel().setStatistics(null);
                            }

                            @Override
                            public Job getJob() {
                                return Job.CLEAR_FACTS_STATS;
                            }
                        });
                        home.setTitle(titleString());
                        sipModel.seekReset();
                        break;
                    default:
                        DataSetModel dataSetModel = sipModel.getDataSetModel();
                        home.setTitle(String.format(
                                titleString() + " - [%s -> %s]",
                                dataSetModel.getDataSet().getSpec(), dataSetModel.getPrefix().toUpperCase()
                        ));
                        sipModel.getReportFileModel().refresh();
                        break;
                }
            }
        });
        attachAccelerator(new QuitAction(), home);
        attachAccelerator(statusPanel.getButtonAction(), home);
    }

    private String titleString() {
        if (version != null) {
            return String.format("SIP-Creator %s", version);
        }
        else {
            return "SIP Creator Test";
        }
    }

    private JPanel createStatePanel() {
        statusPanel.setReaction(ABSENT, allFrames.prepareForNothing());
        statusPanel.setReaction(IMPORTED, new InputAnalyzer());
        statusPanel.setReaction(ANALYZED_IMPORT, allFrames.prepareForDelimiting());
        statusPanel.setReaction(DELIMITED, new ConvertPerformer());
        statusPanel.setReaction(SOURCED, new InputAnalyzer());
        statusPanel.setReaction(ANALYZED_SOURCE, allFrames.prepareForMapping(desktop));
        statusPanel.setReaction(MAPPING, validateAction);
        statusPanel.setReaction(PROCESSED, createSipZipAction);
        JPanel p = new JPanel(new GridLayout(1, 0, 6, 6));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        p.add(statusPanel);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new WorkPanel(sipModel), BorderLayout.CENTER);
        rightPanel.add(createButtonPanel(), BorderLayout.WEST);
        p.add(rightPanel);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        JButton b;
        p.add(b = new JButton(unlockMappingAction));
        b.setHorizontalAlignment(JButton.LEFT);
        p.add(b = new JButton(selectAnotherMappingAction));
        b.setHorizontalAlignment(JButton.LEFT);
        return p;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(allFrames.getViewMenu());
        bar.add(expertMenu);
        return bar;
    }

    private class InputAnalyzer implements Swing {
        @Override
        public void run() {
            sipModel.analyzeFields();
        }
    }

    private class ConvertPerformer implements Swing {
        @Override
        public void run() {
            sipModel.convertSource();
        }
    }

    private void quit() {
        if (!sipModel.getWorkModel().isEmpty()) {
            boolean exitAnyway = feedback.confirm(
                    "Busy",
                    "There are jobs busy, are you sure you want to exit?"
            );
            if (exitAnyway) return;
        }
        System.exit(0);
    }

    private void destroy() {
        sipModel.shutdown();
        resizeTimer.stop();
        home.setVisible(false);
        home.dispose();
    }

    private class ResolverContext implements CachedResourceResolver.Context {
        private Storage storage;
        private HttpClient httpClient;

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public void setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public String get(String url) {
            try {
                HttpGet get = new HttpGet(url);
                HttpResponse response = httpClient.execute(get);
                StatusLine line = response.getStatusLine();
                if (line.getStatusCode() != HttpStatus.SC_OK) {
                    throw new IOException(String.format(
                            "HTTP Error %s (%s) on %s",
                            line.getStatusCode(), line.getReasonPhrase(), url
                    ));
                }
                return EntityUtils.toString(response.getEntity());
            }
            catch (Exception e) {
                throw new RuntimeException("Fetching problem: " + url, e);
            }
        }

        @Override
        public File file(String systemId) {
            return storage.cache(systemId.replaceAll("[/:]", "_"));
        }
    }

    private class QuitAction extends AbstractAction {

        private QuitAction() {
            super("Quit");
            putValue(Action.ACCELERATOR_KEY, MENU_W);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            quit();
        }
    }

    private static class LaunchAction implements Runnable {
        private Application application;

        @Override
        public void run() {
            if (application != null) application.destroy();
            try {
                application = new Application(HomeDirectory.WORK_DIR);
                application.home.setVisible(true);
                application.allFrames.initiate();
                if (version != null && !isLatestVersion(version)) {
                    application.feedback.alert("New SIP-Creator version available at http://sip-creator.delving.eu/");
                }
            }
            catch (StorageException e) {
                JOptionPane.showMessageDialog(null, "Unable to create the storage directory");
                e.printStackTrace();
            }
        }
    }

    private static LaunchAction LAUNCH = new LaunchAction();

    private static void memoryNotConfigured() {
        String os = System.getProperty("os.name");
        Runtime rt = Runtime.getRuntime();
        int totalMemory = (int) (rt.totalMemory() / 1024 / 1024);
        StringBuilder out = new StringBuilder();
        String JAR_NAME = "SIP-Creator-2014-XX-XX.jar";
        if (os.startsWith("Windows")) {
            out.append(":: SIP-Creator Startup Batch file for Windows (more memory than ").append(totalMemory).append("Mb)\n");
            out.append("java -jar -Xms1024m -Xmx1024m ").append(JAR_NAME);
        }
        else if (os.startsWith("Mac")) {
            out.append("# SIP-Creator Startup Script for Mac OSX (more memory than ").append(totalMemory).append("Mb)\n");
            out.append("java -jar -Xms1024m -Xmx1024m ").append(JAR_NAME);
        }
        else {
            System.out.println("Unrecognized OS: " + os);
        }
        String script = out.toString();
        final JDialog dialog = new JDialog(null, "Memory Not Configured Yet!", Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea scriptArea = new JTextArea(3, 40);
        scriptArea.setText(script);
        scriptArea.setSelectionStart(0);
        scriptArea.setSelectionEnd(script.length());
        JPanel scriptPanel = new JPanel(new BorderLayout());
        scriptPanel.setBorder(BorderFactory.createTitledBorder("Script File"));
        scriptPanel.add(scriptArea, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JButton ok = new JButton("OK, Continue anyway");
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
                EventQueue.invokeLater(LAUNCH);
            }
        });
        buttonPanel.add(ok);
        JPanel centralPanel = new JPanel(new GridLayout(0, 1));
        centralPanel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        centralPanel.add(new JLabel(
                "<html><b>The SIP-Creator started directly can have too little default memory allocated." +
                        "<br>It should be started with the following script:</b>"
        ));
        centralPanel.add(scriptPanel);
        centralPanel.add(new JLabel(
                "<html><b>Please copy the above text into a batch or script file and execute that instead.</b>"
        ));
        dialog.getContentPane().add(centralPanel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int) ((dimension.getWidth() - dialog.getWidth()) / 2);
        int y = (int) ((dimension.getHeight() - dialog.getHeight()) / 2);
        dialog.setLocation(x, y);
        dialog.setVisible(true);
    }

    private static String getPomVersion() {
        URL resource = Application.class.getResource("/META-INF/maven/eu.delving/sip-app/pom.properties");
        if (resource != null) {
            try {
                Properties pomProperties = new Properties();
                pomProperties.load(resource.openStream());
                return pomProperties.getProperty("version");
            }
            catch (Exception e) {
                System.err.println("Cannot read maven resource");
            }
        }
        return null;
    }

    private static boolean isLatestVersion(String version) {
        try {
            URL site = new URL("http://sip-creator.delving.eu/version.txt");
            List<String> pageLines = IOUtils.readLines(site.openStream());
            for (String line: pageLines) {
                if (line.equals(version)) return true;
            }
        }
        catch (Exception e) {
            System.err.println("Problem checking version: "+e);
        }
        return false;
    }

    public static void main(final String[] args) throws StorageException {
        version = getPomVersion();
        Runtime rt = Runtime.getRuntime();
        int totalMemory = (int) (rt.totalMemory() / 1024 / 1024);
        if (totalMemory < 900) {
            memoryNotConfigured();
        }
        else {
            EventQueue.invokeLater(LAUNCH);
        }
    }
}

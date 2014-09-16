/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.actions;

import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Upload what is necessary to the culture hub
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class UploadAction extends AbstractAction {
    private SipModel sipModel;
    private NetworkClient networkClient;

    public UploadAction(SipModel sipModel, NetworkClient networkClient) {
        super("Upload");
        putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        this.sipModel = sipModel;
        this.networkClient = networkClient;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        if (sipModel.getDataSetModel().isEmpty()) return;
        if (sipModel.getFeedback().getNarthexCredentials()) {
            String narthexUrl = sipModel.getPreferences().get(Storage.NARTHEX_URL, "");
            String narthexApiKey = sipModel.getPreferences().get(Storage.NARTHEX_API_KEY, "");
            initiateUpload(narthexUrl, narthexApiKey);
        }
    }

    private void initiateUpload(String url,  String apiKey) {
        try {
            networkClient.uploadNarthex(sipModel.getDataSetModel().getDataSet(), url, apiKey, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                }
            });
        }
        catch (final StorageException e) {
            sipModel.getFeedback().alert("Unable to complete uploading", e);
            setEnabled(true);
        }
    }
}

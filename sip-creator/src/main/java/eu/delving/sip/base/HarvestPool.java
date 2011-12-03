package eu.delving.sip.base;

import eu.delving.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contains a pool of running harvests.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class HarvestPool extends AbstractListModel {

    private static final int MAX_THREADS = 5;
    private static final Logger LOG = Logger.getLogger(HarvestPool.class);
    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
    private List<Harvestor> tasks = new ArrayList<Harvestor>();
    private SipModel sipModel;

    public HarvestPool(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void submit(final Harvestor harvestor) {
        if (tasks.contains(harvestor)) {
            LOG.info(String.format("Harvestor for %s is already running", harvestor.getDataSetSpec()));
            return;
        }
        executorService.submit(harvestor);
        harvestor.setListener(
                new Harvestor.Listener() {

                    @Override
                    public void finished(boolean cancelled) {
                        tasks.remove(harvestor);
                        fireIntervalRemoved(this, 0, tasks.size());
                    }

                    @Override
                    public void progress(int count) {
                        fireContentsChanged(this, 0, tasks.size());
                    }

                    @Override
                    public void tellUser(String message) {
                        LOG.info(message);
                        sipModel.getFeedback().say(String.format("Harvestor '%s' %s", harvestor.getDataSetSpec(), message));
                    }

                    @Override
                    public void failed(String message, Exception exception) {
                        if (null == exception) {
                            sipModel.getFeedback().alert(String.format("Harvestor '%s': %s", harvestor.getDataSetSpec(), message));
                        }
                        else {
                            sipModel.getFeedback().alert(String.format("Harvestor '%s': %s", harvestor.getDataSetSpec(), message), exception);
                        }
                        LOG.error(exception);
                        tasks.remove(harvestor);
                        fireIntervalRemoved(this, 0, tasks.size());
                    }

                }
        );
        tasks.add(harvestor);
        fireIntervalAdded(this, 0, tasks.size());
    }

    @Override
    public int getSize() {
        return tasks.size();
    }

    @Override
    public Object getElementAt(int i) {
        return tasks.get(i);
    }
}

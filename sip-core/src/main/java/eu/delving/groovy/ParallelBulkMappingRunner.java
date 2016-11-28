package eu.delving.groovy;

import com.google.common.base.MoreObjects;
import eu.delving.metadata.RecMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Stops processing when it encounters a poison pill.
 */
public class ParallelBulkMappingRunner implements Runnable {

    private final BlockingQueue<MetadataRecord> input;
    private final BlockingQueue<SingleMappingJobResult> output;
    private static final Logger LOG = LoggerFactory.getLogger(ParallelBulkMappingRunner.class);
    private ExecutorService executorService;
    private final CompiledScript script;
    private final RecMapping recMapping;
    private boolean keepRunning = true;

    /**
     * @param input the mapping-records to process
     * @param output the mapped node.
     * @param parallelism the amount of workers. Recommended not to equal to or lower than the amount of cores in this machine as
     *                    this work is CPU-bound.
     */
    public ParallelBulkMappingRunner(BlockingQueue<MetadataRecord> input,
                                     BlockingQueue<SingleMappingJobResult> output,
                                     CompiledScript script,
                                     RecMapping recMapping,
                                     final int parallelism) {
        this.input = input;
        this.output = output;
        this.script = script;
        this.recMapping = recMapping;
        this.executorService = Executors.newWorkStealingPool(parallelism);
    }

    @Override
    public void run() {
        while(keepRunning) {
            try {
                MetadataRecord toDo = input.take();
                executorService.submit(new SingleRecordProcessingJob(toDo));
                if (toDo.isPoison()) {
                    keepRunning = false;
                }

            } catch (InterruptedException e) {
                LOG.warn("Aborting execution");
                executorService.shutdownNow();
                throw new RuntimeException("Execution aborted");
            }
        }
        try {
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(8, TimeUnit.HOURS);
            LOG.debug("Processing did not terminate within 8 hours, giving up.", terminated);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to finish nicely", e);
        }
    }



    private class SingleRecordProcessingJob implements Runnable {
        private final MetadataRecord metadataRecord;

        SingleRecordProcessingJob(MetadataRecord metadataRecord) {
            this.metadataRecord = metadataRecord;
        }

        @Override
        public void run() {
            SingleMappingJobResult singleMappingJobResult;

            if (this.metadataRecord.isPoison()) {
                singleMappingJobResult = new SingleMappingJobResult(metadataRecord, null, null, true);
            } else {
                SimpleBindings bindings = Utils.bindingsFor(recMapping.getFacts(),
                    recMapping.getRecDefTree().getRecDef(), metadataRecord.getRootNode(),
                    recMapping.getRecDefTree().getRecDef().valueOptLookup);
                try {
                    Node result = (Node) script.eval(bindings);
                    Node node = Utils.stripEmptyElements(result);
                    singleMappingJobResult = new SingleMappingJobResult(metadataRecord, node, null, false);
                } catch (ScriptException e) {
                    singleMappingJobResult = new SingleMappingJobResult(metadataRecord, null, e, false);
                }
            }
            try {
                output.put(singleMappingJobResult);
            } catch (InterruptedException e) {
                LOG.warn("Aborting execution");
                executorService.shutdownNow();
            }
        }
    }

    public static class SingleMappingJobResult {
        public final MetadataRecord metadataRecord;
        public final Node result;
        public final Exception exception;
        public final boolean isPoison;

        SingleMappingJobResult(MetadataRecord metadataRecord, Node result, Exception exception, boolean isPoison) {
            this.metadataRecord = metadataRecord;
            this.result = result;
            this.exception = exception;
            this.isPoison = isPoison;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("metadataRecord", metadataRecord)
                .add("result", result)
                .add("exception", exception)
                .add("isPoison", isPoison)
                .toString();
        }
    }
}

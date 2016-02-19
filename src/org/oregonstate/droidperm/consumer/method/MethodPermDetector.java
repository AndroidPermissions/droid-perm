package org.oregonstate.droidperm.consumer.method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class MethodPermDetector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private DPSetupApplication setupApp;

    public void load() {
        setupApp = new DPSetupApplication();
        try {
            setupApp.calculateSourcesSinksEntrypoints("producersConsumers.txt");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Set<SourceSinkDefinition> getProducerDefs() {
        return setupApp.getProducers();
    }

    public Set<SourceSinkDefinition> getConsumerDefs() {
        return setupApp.getConsumers();
    }

    public void analyzeAndPrint() {
        load();
        printResults();
    }

    private void printResults() {
        setupApp.printProducerDefs();
        setupApp.printConsumerDefs();
    }
}

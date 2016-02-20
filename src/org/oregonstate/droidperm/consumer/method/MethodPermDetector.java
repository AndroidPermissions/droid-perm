package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.FixedSourcePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Sources;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private DPSetupApplication setupApp;
    private Set<MethodOrMethodContext> producers;
    private Set<MethodOrMethodContext> consumers;

    private List<Edge> producerInflow;
    private Map<MethodOrMethodContext, List<Edge>> consumerInflows;
    MethodOrMethodContext dummyMainMethod;

    private static MethodPermDetector instance;

    public static MethodPermDetector getInstance() {
        if (instance == null) {
            instance = new MethodPermDetector();
            instance.init();
        }
        return instance;
    }

    public Set<SootMethodAndClass> getProducerDefs() {
        return setupApp.getProducers().stream().map(SourceSinkDefinition::getMethod).collect(Collectors.toSet());
    }

    public Set<SootMethodAndClass> getConsumerDefs() {
        return setupApp.getConsumers().stream().map(SourceSinkDefinition::getMethod).collect(Collectors.toSet());
    }

    public Set<MethodOrMethodContext> getProducers() {
        return producers;
    }

    public Set<MethodOrMethodContext> getConsumers() {
        return consumers;
    }

    private void init() {
        setupApp = new DPSetupApplication();
        try {
            setupApp.calculateSourcesSinksEntrypoints("producersConsumers.txt");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        producers = CallGraphUtil.getActualMethods(getProducerDefs());
        consumers = CallGraphUtil.getActualMethods(getConsumerDefs());

        producerInflow = CallGraphUtil.getInflowCallGraph(producers);

        consumerInflows = new HashMap<>();
        for (MethodOrMethodContext consumer : consumers) {
            consumerInflows.put(consumer, CallGraphUtil.getInflowCallGraph(consumer));
        }
        dummyMainMethod = getDummyMain();
    }

    public static MethodOrMethodContext getDummyMain() {
        SootMethodAndClass dummyMainDef = new SootMethodAndClass("dummyMainMethod", "dummyMainClass", "void",
                Collections.singletonList("java.lang.String[]"));
        Iterator<MethodOrMethodContext> methIt =
                new Sources(new Filter(new FixedSourcePredicate(dummyMainDef.getSignature()))
                        .wrap(Scene.v().getCallGraph().iterator()));
        if (!methIt.hasNext()) {
            throw new RuntimeException("No dummy main method found");
        }
        return methIt.next();
    }

    public void printResults() {
        setupApp.printProducerDefs();
        setupApp.printConsumerDefs();
        printProducers();
        printConsumers();
        printProducerInflow();
        printConsumerInflows();
        computeIntersections();
    }

    private void computeIntersections() {
        System.out.println("\n\nCovered callbacks for each consumer \n====================================");

        for (MethodOrMethodContext consumer : consumers) {
            System.out.println("\nCovered callbacks for: " + consumer + ":");
            Set<Edge> coveredInflow = new HashSet<>(consumerInflows.get(consumer));
            coveredInflow.retainAll(producerInflow);
            Set<MethodOrMethodContext> coveredCallbacks =
                    coveredInflow.stream().filter(e -> e.getSrc().equals(dummyMainMethod)).map(Edge::getTgt)
                            .collect(Collectors.toSet());
            if (coveredCallbacks.isEmpty()) {
                System.out.println("None!");
            } else {
                System.out.println(coveredCallbacks);
            }
        }
        System.out.println();
    }

    public void printProducers() {
        System.out.println("\n\nProducers in the app: \n====================================");
        producers.forEach(System.out::println);
    }

    public void printConsumers() {
        System.out.println("\n\nConsumers in the app: \n====================================");
        consumers.forEach(System.out::println);
    }

    private void printProducerInflow() {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph for producers:\n");

        new LinkedHashSet<>(producerInflow.stream().map(Edge::getSrc).collect(Collectors.toList()))
                .forEach(System.out::println);
    }

    private void printConsumerInflows() {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph for consumers\n");

        for (MethodOrMethodContext meth : consumers) {
            System.out.println("\nConsumer " + meth + " :\n");
            new LinkedHashSet<>(consumerInflows.get(meth).stream().map(Edge::getSrc).collect(Collectors.toList()))
                    .forEach(System.out::println);
        }
    }
}

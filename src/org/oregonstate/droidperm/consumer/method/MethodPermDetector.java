package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.DebugUtil;
import org.oregonstate.droidperm.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.function.Function;
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

    private List<Edge> producerInflow;//could be local except debugging

    /**
     * 1st level map: key = consumer, value = inflow graph as 2nd lvl map.
     * <br/>
     * 2nd level map: key = source node, value = list of edges having that source node.
     */
    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, List<Edge>>> consumerToInflowGraphMap;
    private Set<MethodOrMethodContext> producerCallbacks;
    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> consumerCallbacks;

    private MethodOrMethodContext dummyMainMethod;

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

    public void analyzeAndPrint() {
        long startTime = System.currentTimeMillis();

        analyze();
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        printProducers();
        printConsumers();
        //printProducerInflow();
        //printConsumerInflows();
        //DebugUtil.printTransitiveTargets(consumers);
        printPathsFromCallbackToConsumer();
        printCoveredCallbacks();

        System.out.println("DroidPerm execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() {
        setupApp = new DPSetupApplication();
        try {
            setupApp.calculateSourcesSinksEntrypoints("producersConsumers.txt");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        producers = CallGraphUtil.getActualMethods(getProducerDefs());
        consumers = CallGraphUtil.getActualMethods(getConsumerDefs());

        dummyMainMethod = getDummyMain();

        producerInflow = CallGraphUtil.getInflowCallGraph(producers);

        consumerToInflowGraphMap = consumers.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> CallGraphUtil.getInflowCallGraph(consumer).stream()
                        .filter(edge -> edge.getSrc() != null) //basically elliminates the main method.
                        .collect(Collectors.groupingBy(Edge::getSrc))
        ));

        //DebugUtil.printTargets(consumers);

        producerCallbacks = producerInflow.stream().filter(e -> e.getSrc().equals(dummyMainMethod)).map(Edge::getTgt)
                .collect(Collectors.toSet());

        consumerCallbacks = consumers.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> consumerToInflowGraphMap.get(consumer).get(dummyMainMethod).stream()
                        .map(Edge::getTgt).collect(Collectors.toSet())
        ));
    }

    public static MethodOrMethodContext getDummyMain() {
        SootMethodAndClass dummyMainDef = new SootMethodAndClass("dummyMainMethod", "dummyMainClass", "void",
                Collections.singletonList("java.lang.String[]"));
        final String dummyMainSig = dummyMainDef.getSignature();
        Optional<MethodOrMethodContext> optionalMeth = StreamUtils.asStream(Scene.v().getCallGraph())
                .map(Edge::getSrc)
                .filter(srcMeth -> srcMeth != null && srcMeth.method().getSignature().equals(dummyMainSig))
                .findAny();
        if (!optionalMeth.isPresent()) {
            throw new RuntimeException("No dummy main method found");
        }
        return optionalMeth.get();
    }

    private void printCoveredCallbacks() {
        System.out.println("\n\nCovered callbacks for each consumer \n====================================");

        //sorting methods by toString() efficiently, without computing toString() each time.
        Collection<MethodOrMethodContext> sortedConsumers =
                consumers.stream().collect(Collectors
                        .toMap(Object::toString, Function.identity(), StreamUtils.throwingMerger(), TreeMap::new))
                        .values();

        for (MethodOrMethodContext consumer : sortedConsumers) {
            System.out.println("\nCallbacks for: " + consumer);

            //true for covered callbacks, false for not covered
            Map<Boolean, List<MethodOrMethodContext>> partitionedCallbacks =
                    consumerCallbacks.get(consumer).stream()
                            .collect(Collectors.partitioningBy(producerCallbacks::contains));

            if (!partitionedCallbacks.get(true).isEmpty()) {
                System.out.println("Permission check detected:");
                partitionedCallbacks.get(true).stream()
                        .forEach((MethodOrMethodContext cb) -> System.out.println("    " + cb));
            }

            if (!partitionedCallbacks.get(false).isEmpty()) {
                System.out.println("Permission check NOT detected:");
                partitionedCallbacks.get(false).stream()
                        .forEach(cb -> System.out.println("    " + cb));
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

        producerInflow.stream().map(Edge::getSrc).distinct()
                .forEach(System.out::println);
    }

    private void printConsumerInflows() {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph for consumers\n");

        for (MethodOrMethodContext meth : consumers) {
            System.out.println("\nConsumer " + meth + " :\n");
            consumerToInflowGraphMap.get(meth).forEach(
                    (src, edges) -> {
                        System.out.println("From " + src + " to:");
                        edges.stream().forEach(edge -> System.out.println("    " + edge.getTgt()));
                    });
        }
    }

    private void printPathsFromCallbackToConsumer() {
        System.out.println("\nPaths from each callback to each consumer");
        System.out.println("============================================\n");

        for (MethodOrMethodContext cons : consumers) {
            Map<MethodOrMethodContext, List<Edge>> inflow = consumerToInflowGraphMap.get(cons);
            for (MethodOrMethodContext callback : consumerCallbacks.get(cons)) {
                System.out.println("From " + callback + "\n  to " + cons);
                System.out.println("--------------------------------------------");

                MethodOrMethodContext node = callback;

                //required to prevent infinite loops in case inflow contains recursion
                Set<Edge> traversed = new HashSet<>();
                while (node != null & node != cons) {
                    System.out.println(node);
                    Edge nextEdge =
                            inflow.get(node).stream().filter(edge -> !traversed.contains(edge)).findAny().orElse(null);
                    if (nextEdge != null) {
                        traversed.add(nextEdge);
                        node = nextEdge.getTgt();
                    } else {
                        node = null;
                    }
                }
                if (node != null) {
                    System.out.println(node);
                } else {
                    System.out.println("Error: consumer not reached but inflow has no more nodes!");
                }
                System.out.println();
            }
        }
    }
}

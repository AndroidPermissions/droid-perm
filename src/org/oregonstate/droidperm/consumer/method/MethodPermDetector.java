package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.perm.PermissionDefParser;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private Set<MethodOrMethodContext> permCheckers;
    private Set<MethodOrMethodContext> sensitives;
    private MethodOrMethodContext dummyMainMethod;

    //todo build them through the same algorithm as sensitives
    private List<Edge> permCheckerInflow;//could be local except debugging
    private Set<MethodOrMethodContext> permCheckerCallbacks;

    private CallPathHolder callPathHolder;

    public void analyzeAndPrint() {
        long startTime = System.currentTimeMillis();
        analyze();
        printResults();

        System.out.println("DroidPerm execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() {
        Options.v().set_allow_phantom_refs(false); // prevents PointsToAnalysis from being released

        PermissionDefParser permissionDefParser;
        try {
            permissionDefParser = new PermissionDefParser("PermissionDefs.txt");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<SootMethodAndClass> permCheckerDefs = permissionDefParser.getPermCheckerDefs();
        Set<? extends SootMethodAndClass> sensitiveDefs = permissionDefParser.getSensitiveDefs();

        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        sensitives = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(sensitiveDefs));
        dummyMainMethod = getDummyMain();

        permCheckerInflow = CallGraphUtil.getInflowCallGraph(permCheckers);
        permCheckerCallbacks = permCheckerInflow.stream().filter(e -> e.getSrc().equals(dummyMainMethod)).map(Edge::getTgt)
                .collect(Collectors.toSet());

        //select one of the call path algorithms.
        //callPathHolder = new OutflowCPHolder(dummyMainMethod, sensitives);
        //callPathHolder = new InflowCPHolder(dummyMainMethod, sensitives);
        callPathHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives);

        //DebugUtil.printTargets(sensitives);
    }

    private void printResults() {
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        printProducers();
        printConsumers();
        //printProducerInflow();
        callPathHolder.printPathsFromCallbackToConsumer();
        printCoveredCallbacks();
        //DebugUtil.pointsToTest();
    }

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return CallGraphUtil.getEntryPointMethod(Scene.v().getMethod(sig));
    }

    private void printCoveredCallbacks() {
        System.out.println("\n\nCovered callbacks for each permission/sensitive \n====================================");

        //todo - a map from permission sets to sensitive sets
        /*Map<Set<String>, Set<MethodOrMethodContext>> = sensitives.stream().collect(
                Collectors.groupingBy(sens -> ));*/

        //sorting methods by toString() efficiently, without computing toString() each time.
        Collection<MethodOrMethodContext> sortedSensitives =
                sensitives.stream().collect(Collectors
                        .toMap(Object::toString, Function.identity(), StreamUtil.throwingMerger(), TreeMap::new))
                        .values();

        for (MethodOrMethodContext sensitive : sortedSensitives) {
            System.out.println("\nCallbacks for: " + sensitive);

            //true for covered callbacks, false for not covered
            Map<Boolean, List<MethodOrMethodContext>> partitionedCallbacks =
                    callPathHolder.getCallbacks(sensitive).stream()
                            .collect(Collectors.partitioningBy(permCheckerCallbacks::contains));

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
        permCheckers.forEach(System.out::println);
    }

    public void printConsumers() {
        System.out.println("\n\nConsumers in the app: \n====================================");
        sensitives.forEach(System.out::println);
    }

    private void printProducerInflow() {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph for permCheckers:\n");

        permCheckerInflow.stream().map(Edge::getSrc).distinct()
                .forEach(System.out::println);
    }

}

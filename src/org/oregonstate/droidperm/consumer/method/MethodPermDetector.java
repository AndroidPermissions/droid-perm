package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.perm.PermissionDefParser;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private MethodOrMethodContext dummyMainMethod;

    private Set<MethodOrMethodContext> permCheckers;
    private Map<MethodOrMethodContext, Set<String>> callbackToCheckedPermsMap;

    /**
     * The combined inflow of all checkers.
     * <p>
     * todo build them through the same algorithm as sensitives
     */
    private List<Edge> permCheckerInflow;

    /**
     * Set of callbacks with permission checks.
     */
    private Set<MethodOrMethodContext> permCheckerCallbacks;

    private Map<AndroidMethod, Set<MethodOrMethodContext>> resolvedSensitiveDefs;
    private Set<MethodOrMethodContext> sensitives;

    /**
     * A map from permission sets to sets of resolved sensitive method definitions requiring this permission set.
     */
    private Map<Set<String>, Set<AndroidMethod>> permissionToSensitiveDefMap;

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
        Set<AndroidMethod> sensitiveDefs = permissionDefParser.getSensitiveDefs();

        dummyMainMethod = getDummyMain();

        //checkers
        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        callbackToCheckedPermsMap = buildCallbackToCheckedPermsMap(permCheckers);

        permCheckerInflow = CallGraphUtil.getInflowCallGraph(permCheckers);
        permCheckerCallbacks = permCheckerInflow.stream()
                .filter(e -> e.getSrc().equals(dummyMainMethod)).map(Edge::getTgt).collect(Collectors.toSet());

        //sensitives
        resolvedSensitiveDefs = CallGraphUtil.resolveCallGraphEntriesToMap(sensitiveDefs);

        sensitives = new HashSet<>();
        resolvedSensitiveDefs.values().forEach(sensitives::addAll);

        permissionToSensitiveDefMap = buildPermissionToSensitiveDefMap(resolvedSensitiveDefs.keySet());

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

    private Map<MethodOrMethodContext, Set<String>> buildCallbackToCheckedPermsMap(
            Set<MethodOrMethodContext> permCheckers) {
        CallGraph cg = Scene.v().getCallGraph();

        //key intuition: in both perm. checkers the last argument is the permission
        Stream<Edge> checkerCallStream = permCheckers.stream()
                //all edges invoking permission checks
                .flatMap(checkerMeth -> StreamUtil.asStream(cg.edgesInto(checkerMeth)));
        return null; //todo
    }

    private Map<Set<String>, Set<AndroidMethod>> buildPermissionToSensitiveDefMap(Set<AndroidMethod> permissionDefs) {
        return permissionDefs.stream().collect(Collectors.toMap(
                sensitiveDef -> new HashSet<>(sensitiveDef.getPermissions()),
                sensitiveDef -> new HashSet<>(Collections.singleton(sensitiveDef)),
                StreamUtil::mutableUnion //merge function for values
        ));
    }

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return CallGraphUtil.getEntryPointMethod(Scene.v().getMethod(sig));
    }

    private void printCoveredCallbacks() {
        System.out
                .println("\n\nCovered callbacks for each permission/sensitive \n====================================");

        for (Set<String> permSet : permissionToSensitiveDefMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            //sorting methods by toString() efficiently, without computing toString() each time.
            Collection<MethodOrMethodContext> sortedSensitives = permissionToSensitiveDefMap.get(permSet).stream()
                    .flatMap(sensDef -> resolvedSensitiveDefs.get(sensDef).stream())
                    //collection into TreeMap with keys produced by toString() ensures sorting by toString()
                    .collect(Collectors
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

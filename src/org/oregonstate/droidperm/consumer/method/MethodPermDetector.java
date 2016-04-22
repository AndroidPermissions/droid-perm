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
import soot.options.Options;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    @SuppressWarnings("FieldCanBeLocal")
    private MethodOrMethodContext dummyMainMethod;

    private Set<MethodOrMethodContext> permCheckers;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    @SuppressWarnings("FieldCanBeLocal")
    private CallPathHolder checkerPathsHolder;
    private Map<MethodOrMethodContext, Set<String>> callbackToCheckedPermsMap;

    private Map<AndroidMethod, Set<MethodOrMethodContext>> resolvedSensitiveDefs;
    private Set<MethodOrMethodContext> sensitives;

    /**
     * A map from permission sets to sets of resolved sensitive method definitions requiring this permission set.
     */
    private Map<Set<String>, Set<AndroidMethod>> permissionToSensitiveDefMap;

    private CallPathHolder sensitivePathsHolder;

    public void analyzeAndPrint(String permissionDefFile) {
        long startTime = System.currentTimeMillis();
        analyze(permissionDefFile);
        printResults();

        System.out.println("DroidPerm execution time: " + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze(String permissionDefFile) {
        Options.v().set_allow_phantom_refs(false); // prevents PointsToAnalysis from being released

        PermissionDefParser permissionDefParser;
        try {
            permissionDefParser = new PermissionDefParser(permissionDefFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<SootMethodAndClass> permCheckerDefs = permissionDefParser.getPermCheckerDefs();
        Set<AndroidMethod> sensitiveDefs = permissionDefParser.getSensitiveDefs();

        dummyMainMethod = getDummyMain();

        //checkers
        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);

        //sensitives
        resolvedSensitiveDefs = CallGraphUtil.resolveCallGraphEntriesToMap(sensitiveDefs);

        sensitives = new HashSet<>();
        resolvedSensitiveDefs.values().forEach(sensitives::addAll);

        permissionToSensitiveDefMap = buildPermissionToSensitiveDefMap(resolvedSensitiveDefs.keySet());

        //select one of the call path algorithms.
        //sensitivePathsHolder = new OutflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new InflowCPHolder(dummyMainMethod, sensitives);
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives);

        //DebugUtil.printTargets(sensitives);
    }

    private void printResults() {
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        printCheckers();
        printSensitives();
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        printCoveredCallbacks();
        //DebugUtil.pointsToTest();
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
            String perm = permSet.iterator().next();//one perm per sensitive for now

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
                        sensitivePathsHolder.getReachableCallbacks(sensitive).stream()
                                .collect(Collectors.partitioningBy(
                                        callback -> callbackToCheckedPermsMap.get(callback) != null
                                                && callbackToCheckedPermsMap.get(callback).contains(perm)));

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

    public void printCheckers() {
        System.out.println("\n\nCheckers in the app: \n====================================");
        permCheckers.forEach(System.out::println);
    }

    public void printSensitives() {
        System.out.println("\n\nSensitives in the app: \n====================================");
        sensitives.forEach(System.out::println);
    }

}

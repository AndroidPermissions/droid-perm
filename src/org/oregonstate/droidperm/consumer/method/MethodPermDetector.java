package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.perm.PermissionDefParser;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
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
    private Map<MethodOrMethodContext, AndroidMethod> sensitiveToSensitiveDefMap;
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
        sensitiveToSensitiveDefMap = buildSensitiveToSensitiveDefMap();

        sensitives = resolvedSensitiveDefs.values().stream().collect(MyCollectors.toFlatSet());
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
        printReachableSensitivesInCallbackStmts();
        //DebugUtil.pointsToTest();
    }

    private Map<MethodOrMethodContext, AndroidMethod> buildSensitiveToSensitiveDefMap() {
        return resolvedSensitiveDefs.keySet().stream().map(def ->
                resolvedSensitiveDefs.get(def).stream().collect(Collectors.toMap(sens -> sens, sens -> def))
        ).collect(MyCollectors.toFlatMap());
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
                Map<PermCheckStatus, List<MethodOrMethodContext>> permCheckStatusToCallbacks =
                        sensitivePathsHolder.getReachableCallbacks(sensitive).stream()
                                .collect(Collectors.groupingBy(
                                        callback -> getPermCheckStatus(perm, callback)));

                for (PermCheckStatus status : PermCheckStatus.values()) {
                    if (permCheckStatusToCallbacks.get(status) != null) {
                        System.out.println("Perm check " + status + ":");
                        for (MethodOrMethodContext callback : permCheckStatusToCallbacks.get(status)) {
                            System.out.println("    " + callback);
                            //DebugUtil.printCallClassesAndLineNumbers(sensitive, callback, sensitivePathsHolder);
                        }
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    private PermCheckStatus getPermCheckStatus(String perm, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (callbackToCheckedPermsMap.get(callback).contains(perm)) {
                return PermCheckStatus.DETECTED;
            } else if (!callbackToCheckedPermsMap.get(callback).isEmpty()) {
                return PermCheckStatus.UNRELATED;
            }
        }
        return PermCheckStatus.NOT_DETECTED;
    }

    private void printReachableSensitivesInCallbackStmts() {
        System.out.println("\nRequired permissions for code inside each callback:");
        System.out.println("========================================================================");

        CallGraph cg = Scene.v().getCallGraph();
        for (MethodOrMethodContext callback : sensitivePathsHolder.getReachableCallbacks()) {
            System.out.println("\n" + callback + " :");
            for (Unit unit : callback.method().getActiveBody().getUnits()) {
                Set<MethodOrMethodContext> sensitives = StreamUtil.asStream(cg.edgesOutOf(unit))
                        .map(edge -> sensitivePathsHolder.getReacheableSensitives(edge))
                        .filter(set -> set != null)
                        .collect(MyCollectors.toFlatSet());
                if (!sensitives.isEmpty()) {
                    System.out.println("    " + unit.getJavaSourceStartLineNumber() + ": "
                            + ((Stmt) unit).getInvokeExpr().getMethod() + " : " + getPermissionsFor(sensitives));
                }
            }
        }
        System.out.println();
    }

    public Set<String> getPermissionsFor(Collection<MethodOrMethodContext> sensitives) {
        return sensitives.stream().map(sens -> sensitiveToSensitiveDefMap.get(sens)).map(AndroidMethod::getPermissions)
                .collect(MyCollectors.toFlatSet());
    }

    public void printCheckers() {
        System.out.println("\n\nCheckers in the app: \n====================================");
        permCheckers.forEach(System.out::println);
    }

    public void printSensitives() {
        System.out.println("\n\nSensitives in the app: \n====================================");
        sensitives.forEach(System.out::println);
    }

    public enum PermCheckStatus {
        DETECTED("Permission check detected"),
        UNRELATED("Unrelated permission check detected"),
        NOT_DETECTED("No permission check detected");

        private String description;

        PermCheckStatus(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

}

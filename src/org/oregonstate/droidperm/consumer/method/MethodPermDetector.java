package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.jaxb.*;
import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.oregonstate.droidperm.perm.TxtPermissionDefParser;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.scalar.Pair;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);
    private static final File outflowIgnoreListFile = new File("OutflowIgnoreList.txt");

    private File permissionDefFile;
    private File txtOut;
    private File xmlOut;
    private Set<SootMethod> outflowIgnoreSet;

    @SuppressWarnings("FieldCanBeLocal")
    private MethodOrMethodContext dummyMainMethod;

    @SuppressWarnings("FieldCanBeLocal")
    private Set<MethodOrMethodContext> permCheckers;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    //this is actually required for flow-sensitive analysis.
    @SuppressWarnings("FieldCanBeLocal")
    private ContextSensOutflowCPHolder checkerPathsHolder;
    private Map<MethodOrMethodContext, Set<String>> callbackToCheckedPermsMap;

    /**
     * Map from permissions to checkers that check for that permission.
     * <p>
     * 2nd level map: from checker ( Pair[Edge, ParentEdge] ) to safety status. True means safe, the checker described
     * by this edge only checks for one permission. False means unsafe: multiple contexts use this checker to check for
     * multiple permissions, so not all contexts check for all permissions.
     */
    private Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> permsToCheckersMap;

    private Map<AndroidMethod, Set<MethodOrMethodContext>> resolvedSensitiveDefs;
    private Map<MethodOrMethodContext, AndroidMethod> sensitiveToSensitiveDefMap;

    /**
     * This set is sorted.
     */
    private LinkedHashSet<MethodOrMethodContext> sensitives;

    /**
     * A map from permission sets to sets of resolved sensitive method definitions requiring this permission set.
     */
    private Map<Set<String>, Set<AndroidMethod>> permissionToSensitiveDefMap;

    private Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> permsToSensitivesMap;
    private ContextSensOutflowCPHolder sensitivePathsHolder;

    private Map<MethodOrMethodContext, Set<String>> callbackToRequiredPermsMap;
    private Set<String> sometimesNotCheckedPerms;

    /**
     * Map from callback to lvl 2 map describing checked permissions in this callback.
     * <p>
     * Lvl2 map: from checked permissions to usage status of this check: used, unused or possibly used through ICC.
     */
    private Map<MethodOrMethodContext, Map<String, CheckerUsageStatus>> callbackCheckerStatusMap;

    private JaxbCallbackList jaxbData;

    public MethodPermDetector(File permissionDefFile, File txtOut, File xmlOut) {
        this.permissionDefFile = permissionDefFile;
        this.txtOut = txtOut;
        this.xmlOut = xmlOut;
    }

    public void analyzeAndPrint() {
        long startTime = System.currentTimeMillis();
        logger.warn("\n\n"
                + "Start of DroidPerm logs\n"
                + "========================================================================\n");
        analyze();
        printResults();

        System.out.println("\n\nDroidPerm execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() {
        Options.v().set_allow_phantom_refs(false); // prevents PointsToAnalysis from being released

        IPermissionDefProvider permissionDefProvider;
        try {
            permissionDefProvider = new TxtPermissionDefParser(permissionDefFile);
            outflowIgnoreSet = OutflowIgnoreListLoader.load(outflowIgnoreListFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<SootMethodAndClass> permCheckerDefs = permissionDefProvider.getPermCheckerDefs();
        Set<AndroidMethod> sensitiveDefs = permissionDefProvider.getSensitiveDefs();

        dummyMainMethod = getDummyMain();
        permCheckers = CallGraphUtil.getNodesFor(HierarchyUtil.resolveAbstractDispatches(permCheckerDefs));
        resolvedSensitiveDefs = CallGraphUtil.resolveCallGraphEntriesToMap(sensitiveDefs);
        sensitives = resolvedSensitiveDefs.values().stream().flatMap(Collection::stream)
                .sorted(SortUtil.methodOrMCComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        //sensitives should be added to ignore method list, to prevent their body from being analyzed
        outflowIgnoreSet.addAll(
                sensitives.stream().map(MethodOrMethodContext::method).collect(Collectors.toList())
        );

        //checkers

        logger.info("Processing checkers");
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers, outflowIgnoreSet);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);
        permsToCheckersMap = CheckerUtil.buildPermsToCheckersMap(permCheckers);

        //sensitives
        sensitiveToSensitiveDefMap = buildSensitiveToSensitiveDefMap();
        permissionToSensitiveDefMap = buildPermissionToSensitiveDefMap(resolvedSensitiveDefs.keySet());
        permsToSensitivesMap = buildPermsToSensitivesMap();

        logger.info("Processing sensitives");
        //select one of the call path algorithms.
        //sensitivePathsHolder = new OutflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new InflowCPHolder(dummyMainMethod, sensitives);
        //sensitivePathsHolder = new PartialPointsToCPHolder(dummyMainMethod, sensitives, outflowIgnoreSet);
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives, outflowIgnoreSet);

        callbackToRequiredPermsMap = buildCallbackToRequiredPermsMap();
        sometimesNotCheckedPerms = buildSometimesNotCheckedPerms();
        callbackCheckerStatusMap = buildCheckerStatusMap();
        jaxbData = JaxbUtil.buildJaxbData(this);
        //DebugUtil.printTargets(sensitives);
    }

    private void printResults() {
        //setupApp.printProducerDefs();
        //setupApp.printConsumerDefs();
        DebugUtil.logClassesWithCallbacks(sensitivePathsHolder.getUiCallbacks());
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        printReachableSensitivesInCallbackStmts(jaxbData, System.out);

        //printPermCheckStatusPerCallbacks();
        printCheckersInContext(true);
        printSensitivesInContext(true);

        printCheckersInContext(false);
        printSensitivesInContext(false);

        //Printing to files
        if (txtOut != null) {
            try (PrintStream summaryOut = new PrintStream(new FileOutputStream(txtOut))) {
                printReachableSensitivesInCallbackStmts(jaxbData, summaryOut);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        if (xmlOut != null) {
            try {
                JaxbUtil.save(jaxbData, xmlOut);
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        }
        //DebugUtil.pointsToTest();
    }

    private Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> buildPermsToSensitivesMap() {
        return sensitives.stream().collect(
                Collectors.groupingBy(this::getPermissionsFor, Collectors.toCollection(LinkedHashSet::new)));
    }

    private Set<String> getPermissionsFor(MethodOrMethodContext sensitive) {
        return permissionToSensitiveDefMap.keySet().stream().filter(
                permSet -> {
                    Set<AndroidMethod> sensitiveDefs = permissionToSensitiveDefMap.get(permSet);
                    return sensitiveDefs.stream().map(sensDef -> resolvedSensitiveDefs.get(sensDef))
                            .anyMatch(methSet -> methSet.contains(sensitive));
                }
        ).findAny().orElse(null);
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

    private Map<PermCheckStatus, List<MethodOrMethodContext>> getPermCheckStatusToCallbacksMap(
            MethodInContext sensInC, Set<String> permSet) {
        Set<MethodOrMethodContext> reachableCallbacks = getReachingCallbacks(sensInC, sensitivePathsHolder);
        if (reachableCallbacks == null) {
            reachableCallbacks = new HashSet<>();
        }
        return reachableCallbacks.stream().sorted(SortUtil.methodOrMCComparator).collect(
                Collectors.groupingBy(callback -> getPermCheckStatusForAny(permSet, callback)));
    }

    private Set<MethodOrMethodContext> getReachingCallbacks(MethodInContext methInC,
                                                            ContextSensOutflowCPHolder pathsHolder) {
        return pathsHolder.getSensitiveInCToCallbacksMap().get(methInC);
    }

    /**
     * @return A value indicating whether the permissions in this set are checked by permission checks in the given
     * callback. Checks for multiple permissions are linked by OR relationship.
     * <p>
     * For multiple permissions OR (ANY ONE OF) is the most commonly used case, to account for the 2 Location
     * permissions.
     */
    private PermCheckStatus getPermCheckStatusForAny(Set<String> permSet, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (!Collections.disjoint(permSet, callbackToCheckedPermsMap.get(callback))) {
                return PermCheckStatus.CHECK_DETECTED;
            }
        }
        return PermCheckStatus.CHECK_NOT_DETECTED;
    }

    /**
     * @return A value indicating whether the permissions in this set are checked by permission checks in the given
     * callback. Checks for multiple permissions are linked by AND relationship.
     */
    public PermCheckStatus getPermCheckStatusForAll(Collection<String> permSet, MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (callbackToCheckedPermsMap.get(callback).containsAll(permSet)) {
                return PermCheckStatus.CHECK_DETECTED;
            }
        }
        return PermCheckStatus.CHECK_NOT_DETECTED;
    }

    public Map<String, CheckerUsageStatus> getCheckerStatusMap(MethodOrMethodContext callback) {
        return callbackCheckerStatusMap.get(callback);
    }

    private static void printReachableSensitivesInCallbackStmts(JaxbCallbackList data, PrintStream out) {
        out.println("\nOutput for droid-perm-plugin, required permissions for statements directly inside callbacks:");
        out.println("========================================================================");

        for (JaxbCallback callback : data.getCallbacks()) {
            out.println("\n" + callback + " :");
            for (JaxbStmt jaxbStmt : callback.getStmts()) {
                String checkMsg = jaxbStmt.allGuarded() ? "" : " --- checks INCOMPLETE";
                out.println("    " + jaxbStmt.getLine() + ": "
                        + jaxbStmt.getCallFullSignature() + " : " + jaxbStmt.getPermDisplayStrings() + checkMsg);
            }
        }
    }

    public Set<String> getPermissionsFor(Collection<MethodOrMethodContext> sensitives) {
        return sensitives.stream().map(sens -> sensitiveToSensitiveDefMap.get(sens)).map(AndroidMethod::getPermissions)
                .collect(MyCollectors.toFlatSet());
    }

    /**
     * All sensitives in context are printed here, including those that are not reachable from callbacks.
     */
    public void printSensitivesInContext(boolean printCallbacks) {
        String noCallbacksHeader = "Sensitives in context in the call graph:";
        String callbacksHeader = "Sensitives in context in the call graph, with reaching callbacks:";
        String header = printCallbacks ? callbacksHeader : noCallbacksHeader;
        System.out.println(
                "\n\n" + header + " \n========================================================================");
        CallGraph cg = Scene.v().getCallGraph();
        for (Set<String> permSet : permsToSensitivesMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            Map<Set<PermCheckStatus>, Integer> sensitivesCountByStatus = new HashMap<>();
            for (MethodOrMethodContext sens : permsToSensitivesMap.get(permSet)) {
                boolean printed = false;
                Iterable<Edge> edgesInto = IteratorUtil.asIterable(cg.edgesInto(sens));

                for (Edge edgeInto : edgesInto) {
                    //We don't print sensitives whose context is another sensitive.
                    //noinspection ConstantConditions
                    if (sens.method().getDeclaringClass() != edgeInto.src().getDeclaringClass()) {
                        if (!printed) {
                            System.out.println("\nSensitive " + sens);
                            printed = true;
                        }
                        System.out.println("\tfrom " + edgeInto.getSrc());
                        if (TryCatchCheckerUtil.isTryCatchChecked(edgeInto)) {
                            System.out.println("\t\tTRY-CATCH CHECKED");
                        }

                        MethodInContext sensInC = new MethodInContext(edgeInto);
                        Map<PermCheckStatus, List<MethodOrMethodContext>> permCheckStatusToCallbacks =
                                getPermCheckStatusToCallbacksMap(sensInC, permSet);
                        if (outflowIgnoreSet.contains(edgeInto.src())) {
                            System.out.println("\t\tCallbacks: BLOCKED");
                        } else if (!permCheckStatusToCallbacks.isEmpty()) {
                            for (PermCheckStatus status : PermCheckStatus.values()) {
                                if (permCheckStatusToCallbacks.get(status) != null) {
                                    System.out.println("\t\tCallbacks where " + status + ": "
                                            + permCheckStatusToCallbacks.get(status).size());
                                    if (printCallbacks) {
                                        permCheckStatusToCallbacks.get(status).forEach(
                                                meth -> System.out.println("\t\t\t" + meth));
                                    }
                                }
                            }
                        } else {
                            System.out.println("\t\tCallbacks: NONE, POSSIBLY BLOCKED");
                        }

                        //count by status
                        Set<PermCheckStatus> statuses = permCheckStatusToCallbacks.keySet();
                        int oldCount =
                                sensitivesCountByStatus.containsKey(statuses) ? sensitivesCountByStatus.get(statuses)
                                                                              : 0;
                        sensitivesCountByStatus.put(statuses, oldCount + 1);
                    }
                }
            }

            if (!printCallbacks) {
                System.out.println();
                sensitivesCountByStatus.keySet().forEach(statuses
                        -> System.out.println(
                        "Total sensitives+context with status " + statuses
                                + " : " + sensitivesCountByStatus.get(statuses))
                );
            }
        }
    }

    /**
     * All checkers in context are printed here, including those that are not reachable from callbacks.
     */
    public void printCheckersInContext(boolean printCallbacks) {
        String noCallbacksHeader = "Checkers in context in the call graph:";
        String callbacksHeader = "Checkers in context in the call graph, with reaching callbacks:";
        String header = printCallbacks ? callbacksHeader : noCallbacksHeader;
        System.out.println(
                "\n\n" + header + " \n========================================================================");

        for (String perm : permsToCheckersMap.keySet()) {
            System.out.println("\n" + perm + "\n------------------------------------");

            Map<Set<CheckerUsageStatus>, Integer> checkersCountByStatus = new HashMap<>();
            MethodOrMethodContext oldSens = null;
            for (Pair<Edge, Edge> checkerPair : permsToCheckersMap.get(perm).keySet()) {
                Edge checkerEdge = checkerPair.getO1();
                Edge parent = checkerPair.getO2();
                MethodOrMethodContext checkerMeth = checkerEdge.getTgt();
                //We don't print checkers whose context is another checker.
                //noinspection ConstantConditions
                if (checkerMeth.method().getDeclaringClass() != checkerEdge.src().getDeclaringClass()) {
                    if (checkerMeth != oldSens) {
                        System.out.println("\nChecker " + checkerMeth);
                        oldSens = checkerMeth;
                    }
                    System.out.println("\tfrom lvl1 " + checkerEdge.getSrc());
                    System.out.println("\tfrom lvl2 " + parent.getSrc());
                    if (!permsToCheckersMap.get(perm).get(checkerPair)) {
                        System.out.println("\t\tPoints-to certainty: UNCERTAIN");
                    }

                    Map<CheckerUsageStatus, List<MethodOrMethodContext>> checkerUsageStatusToCallbacks =
                            getCheckerUsageStatusToCallbacksMap(checkerPair, perm);
                    if (outflowIgnoreSet.contains(checkerEdge.src()) || outflowIgnoreSet.contains(parent.src())) {
                        System.out.println("\t\tCallbacks: BLOCKED");
                    } else if (!checkerUsageStatusToCallbacks.isEmpty()) {
                        for (CheckerUsageStatus status : checkerUsageStatusToCallbacks.keySet()) {
                            System.out.println("\t\tCallbacks where " + status + ": "
                                    + checkerUsageStatusToCallbacks.get(status).size());
                            if (printCallbacks) {
                                checkerUsageStatusToCallbacks.get(status).forEach(
                                        meth -> System.out.println("\t\t\t" + meth));
                            }
                        }
                    } else {
                        System.out.println("\t\tCallbacks: NONE, POSSIBLY BLOCKED");
                    }

                    //count by status
                    Set<CheckerUsageStatus> statuses = checkerUsageStatusToCallbacks.keySet();
                    int oldCount =
                            checkersCountByStatus.containsKey(statuses) ? checkersCountByStatus.get(statuses)
                                                                        : 0;
                    checkersCountByStatus.put(statuses, oldCount + 1);
                }
            }

            if (!printCallbacks) {
                System.out.println();
                checkersCountByStatus.keySet().forEach(statuses
                        -> System.out.println(
                        "Total checkers+context with status " + statuses + " : " + checkersCountByStatus.get(statuses))
                );
            }
        }
    }

    /**
     * @return map from CheckerUsageStatus to callbacks with this status
     */
    private Map<CheckerUsageStatus, List<MethodOrMethodContext>> getCheckerUsageStatusToCallbacksMap(
            Pair<Edge, Edge> checkerPair, String perm) {
        Set<MethodOrMethodContext> reachableCallbacks = getReachingCallbacks(checkerPair, checkerPathsHolder);
        if (reachableCallbacks == null) {
            reachableCallbacks = new HashSet<>();
        }
        return reachableCallbacks.stream().sorted(SortUtil.methodOrMCComparator).collect(
                Collectors.groupingBy(callback -> getCheckUsageStatus(callback, perm)));
    }

    /**
     * If parent edge doesn't come directly from dummy main, we'll get callbacks for the parent. Otherwise - callbacks
     * for the child.
     * <p>
     * Limitation: This implementation doesn't account for parent-child points-to consistency. Only for checkers.
     */
    private Set<MethodOrMethodContext> getReachingCallbacks(Pair<Edge, Edge> methParentPair,
                                                            ContextSensOutflowCPHolder pathsHolder) {
        Edge meth = methParentPair.getO1();
        Edge parent = methParentPair.getO2();
        Edge target = parent.getSrc() != dummyMainMethod ? parent : meth;
        return pathsHolder.getReachingCallbacks(new MethodInContext(target));
    }

    public CallPathHolder getSensitivePathsHolder() {
        return sensitivePathsHolder;
    }

    public enum PermCheckStatus {
        CHECK_DETECTED("Permission check detected"),
        CHECK_NOT_DETECTED("No permission check detected");

        private String description;

        PermCheckStatus(String description) {
            this.description = description;
        }

        @SuppressWarnings("unused")
        public String description() {
            return description;
        }
    }

    private Map<MethodOrMethodContext, Set<String>> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getReachableCallbacks().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> sensitivePathsHolder.getCallsToSensitiveFor(callback).stream()
                        .map(sensitiveCall -> sensitiveToSensitiveDefMap.get(sensitiveCall.getTgt()).getPermissions())
                        .collect(MyCollectors.toFlatSet())
        ));
    }

    private Set<String> buildSometimesNotCheckedPerms() {
        return callbackToRequiredPermsMap.keySet().stream().flatMap(callback ->
                callbackToRequiredPermsMap.get(callback).stream()
                        //only keep permissions that are required but not checked, globally
                        .filter(perm -> callbackToCheckedPermsMap.get(callback) == null ||
                                !callbackToCheckedPermsMap.get(callback).contains(perm))
        ).collect(Collectors.toSet());
    }

    private Map<MethodOrMethodContext, Map<String, CheckerUsageStatus>> buildCheckerStatusMap() {
        return callbackToCheckedPermsMap.keySet().stream().collect(Collectors.toMap(
                callback -> callback,
                callback -> {
                    Set<String> perms = callbackToCheckedPermsMap.get(callback);

                    return perms.stream().collect(Collectors.toMap(
                            perm -> perm,
                            perm -> getCheckUsageStatus(callback, perm)
                    ));
                }
        ));
    }

    private CheckerUsageStatus getCheckUsageStatus(MethodOrMethodContext callback, String perm) {
        Set<String> reqPerms = callbackToRequiredPermsMap.get(callback);
        reqPerms = reqPerms != null ? reqPerms : Collections.emptySet();

        if (reqPerms.contains(perm)) {
            return CheckerUsageStatus.USED;
        } else if (sometimesNotCheckedPerms.contains(perm)) {
            return CheckerUsageStatus.UNUSED_POSSIBLY_ICC;
        } else {
            return CheckerUsageStatus.UNUSED;
        }
    }

    @SuppressWarnings("unused")
    private void printPermCheckStatusPerCallbacks() {
        System.out.println("\nChecked permissions inside each callback:");
        System.out.println("========================================================================");

        for (MethodOrMethodContext callback : checkerPathsHolder.getReachableCallbacks()) {
            //now callbacks are nicely sorted
            System.out.println("\n" + callback + " :");
            for (String perm : callbackCheckerStatusMap.get(callback).keySet()) {
                CheckerUsageStatus status = callbackCheckerStatusMap.get(callback).get(perm);
                String statusString = status == CheckerUsageStatus.USED ? "used"
                                                                        : status == CheckerUsageStatus.UNUSED
                                                                          ? "NOT used"
                                                                          : "NOT used POSSIBLY ICC";
                System.out.printf("    %-50s  status: %-20s\n", perm, statusString);
            }
        }
        System.out.println();
    }

}

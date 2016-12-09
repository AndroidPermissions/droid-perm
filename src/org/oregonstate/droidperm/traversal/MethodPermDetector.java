package org.oregonstate.droidperm.traversal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import org.oregonstate.droidperm.debug.DebugUtil;
import org.oregonstate.droidperm.jaxb.*;
import org.oregonstate.droidperm.scene.ClasspathFilter;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private File txtOut;
    private File xmlOut;
    private ScenePermissionDefService scenePermDef;

    private ClasspathFilter classpathFilter;

    @SuppressWarnings("FieldCanBeLocal")
    private MethodOrMethodContext dummyMainMethod;

    @SuppressWarnings("FieldCanBeLocal")
    private Set<MethodOrMethodContext> permCheckers;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    //this is actually required for flow-sensitive analysis.
    @SuppressWarnings("FieldCanBeLocal")
    private ContextSensOutflowCPHolder checkerPathsHolder;
    private SetMultimap<MethodOrMethodContext, String> callbackToCheckedPermsMap;

    /**
     * Map from permission to checkers that check for it.
     * <p>
     * 2nd level map: from checker ( Pair[Edge, ParentEdge] ) to safety status. True means safe, the checker described
     * by this edge only checks for one permission. False means unsafe: multiple contexts use this checker to check for
     * multiple permissions, so not all contexts check for all permissions.
     * <p>
     * The key on 1st level map as well as parentEdge in 2nd level map may be null, if the checker was not fully
     * resolved.
     */
    private Table<String, Pair<Edge, Edge>, Boolean> permsToCheckersMap;

    /**
     * This set is sorted.
     */
    private LinkedHashSet<MethodOrMethodContext> sensitives;

    private ContextSensOutflowCPHolder sensitivePathsHolder;

    private SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap;
    private Set<String> sometimesNotCheckedPerms;

    private JaxbCallbackList jaxbData;

    public MethodPermDetector(File txtOut, File xmlOut, ScenePermissionDefService scenePermDef,
                              ClasspathFilter classpathFilter) {
        this.txtOut = txtOut;
        this.xmlOut = xmlOut;
        this.scenePermDef = scenePermDef;
        this.classpathFilter = classpathFilter;
    }

    public void analyzeAndPrint() throws Exception {
        long startTime = System.currentTimeMillis();
        logger.info("\n\n"
                + "Start of DroidPerm logs\n"
                + "========================================================================\n");
        analyze();
        printResults();

        System.out.println("\n\nDroidPerm execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private void analyze() throws Exception {
        dummyMainMethod = getDummyMain();
        permCheckers = CallGraphUtil.getNodesFor(scenePermDef.getPermCheckers());
        sensitives = CallGraphUtil.getNodesFor(scenePermDef.getSceneMethodSensitives()).stream()
                .sorted(SortUtil.methodOrMCComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        logger.info("Processing checkers");
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers, classpathFilter);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);
        permsToCheckersMap = CheckerUtil.buildPermsToCheckersMap(permCheckers);

        logger.info("Processing sensitives");
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, sensitives, classpathFilter);
        callbackToRequiredPermsMap = buildCallbackToRequiredPermsMap();

        //Data structures that combine checkers and sensitives
        sometimesNotCheckedPerms = buildSometimesNotCheckedPerms();

        jaxbData = JaxbUtil.buildJaxbData(this);
    }

    private void printResults() throws Exception {
        DebugUtil.logClassesWithCallbacks(sensitivePathsHolder.getUiCallbacks());
        DebugUtil.logFragments(sensitivePathsHolder.getUiCallbacks());
        sensitivePathsHolder.printPathsFromCallbackToSensitive();
        printReachableSensitivesInCallbackStmts(jaxbData, System.out);

        UndetectedItemsUtil.printUndetectedCheckers(scenePermDef, getPrintedCheckEdges(), classpathFilter);
        UndetectedItemsUtil.printUndetectedSensitives(scenePermDef, getSensitiveEdgesInto(), classpathFilter);

        printCheckersInContext(true);
        printSensitivesInContext(true);

        printCheckersInContext(false);
        printSensitivesInContext(false);

        //Printing to files
        if (txtOut != null) {
            try (PrintStream summaryOut = new PrintStream(new FileOutputStream(txtOut))) {
                printReachableSensitivesInCallbackStmts(jaxbData, summaryOut);
            }
        }
        if (xmlOut != null) {
            JaxbUtil.save(jaxbData, xmlOut);
        }
    }

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return CallGraphUtil.getEntryPointMethod(Scene.v().getMethod(sig));
    }

    private Set<String> getPermissionsFor(MethodOrMethodContext sensitive) {
        return scenePermDef.getPermissionsFor(sensitive.method());
    }

    public Set<String> getPermissionsFor(Collection<MethodOrMethodContext> sensitives) {
        return sensitives.stream().map(this::getPermissionsFor).collect(MyCollectors.toFlatSet());
    }

    private SetMultimap<MethodOrMethodContext, String> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getReachableCallbacks().stream().collect(MyCollectors.toMultimap(
                callback -> callback,
                callback -> sensitivePathsHolder.getCallsToSensitiveFor(callback).stream()
                        .flatMap(sensitiveCall ->
                                scenePermDef.getPermissionsFor(sensitiveCall.getTgt().method()).stream())
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

    private void printReachableSensitivesInCallbackStmts(JaxbCallbackList data, PrintStream out) {
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
        Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> permsToSensitivesMap = buildPermsToSensitivesMap();
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
                        System.out.println("\tfrom " + PrintUtil.toMethodLogString(edgeInto.srcStmt()));
                        if (TryCatchCheckerUtil.isTryCatchChecked(edgeInto)) {
                            System.out.println("\t\tTRY-CATCH CHECKED");
                        }

                        MethodInContext sensInC = new MethodInContext(edgeInto);
                        ListMultimap<PermCheckStatus, MethodOrMethodContext> permCheckStatusToCallbacks =
                                getPermCheckStatusToCallbacksMap(sensInC, permSet);
                        if (!classpathFilter.test(edgeInto.src())) {
                            System.out.println("\t\tCallbacks: BLOCKED");
                        } else if (!permCheckStatusToCallbacks.isEmpty()) {
                            List<MethodOrMethodContext> callbacks = permCheckStatusToCallbacks.values().stream()
                                    .collect(Collectors.toList());
                            System.out.println("\t\tCallback types: " + CallbackTypeUtil.getCallbackTypes(callbacks));
                            for (PermCheckStatus status : PermCheckStatus.values()) {
                                List<MethodOrMethodContext> methodsForStatus = permCheckStatusToCallbacks.get(status);
                                if (!methodsForStatus.isEmpty()) {
                                    System.out.println(
                                            "\t\tCallbacks where " + status + ": " + methodsForStatus.size());
                                    if (printCallbacks) {
                                        methodsForStatus.forEach(
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


    private ListMultimap<PermCheckStatus, MethodOrMethodContext> getPermCheckStatusToCallbacksMap(
            MethodInContext sensInC, Set<String> permSet) {
        Set<MethodOrMethodContext> reachableCallbacks = sensitivePathsHolder.getReachingCallbacks(sensInC);
        if (reachableCallbacks == null) {
            reachableCallbacks = Collections.emptySet();
        }
        //noinspection Convert2MethodRef
        return reachableCallbacks.stream().sorted(SortUtil.methodOrMCComparator).collect(
                MyCollectors.toMultimapGroupingBy(() -> ArrayListMultimap.create(),
                        callback -> getPermCheckStatusForAny(permSet, callback)));
    }

    /**
     * All checkers in context are printed here, including those that are not reachable from callbacks.
     * <p>
     * Map permsToCheckersMap may contain null keys. Also parent edge in pair maybe null.
     */
    public void printCheckersInContext(boolean printCallbacks) {
        String noCallbacksHeader = "Checkers in context in the call graph:";
        String callbacksHeader = "Checkers in context in the call graph, with reaching callbacks:";
        String header = printCallbacks ? callbacksHeader : noCallbacksHeader;
        System.out.println(
                "\n\n" + header + " \n========================================================================");

        for (String perm : permsToCheckersMap.rowKeySet()) {
            String displayPerm = perm.equals("") ? "PERMISSION VALUES UNKNOWN" : perm;
            System.out.println("\n" + displayPerm + "\n------------------------------------");

            Map<Set<CheckerUsageStatus>, Integer> checkersCountByStatus = new HashMap<>();
            MethodOrMethodContext oldSens = null;
            for (Pair<Edge, Edge> checkerPair : permsToCheckersMap.row(perm).keySet()) {
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
                    System.out.println("\tfrom lvl1 " + PrintUtil.toMethodLogString(checkerEdge.srcStmt()));
                    System.out.println("\tfrom lvl2 "
                            + (parent != null ? PrintUtil.toMethodLogString(parent.srcStmt()) : null));
                    if (!permsToCheckersMap.get(perm, checkerPair)) {
                        System.out.println("\t\tPoints-to certainty: UNCERTAIN");
                    }

                    ListMultimap<CheckerUsageStatus, MethodOrMethodContext> checkerUsageStatusToCallbacks =
                            getCheckerUsageStatusToCallbacksMap(checkerPair, perm);
                    if (!classpathFilter.test(checkerEdge.src())
                            || (parent != null && !classpathFilter.test(parent.src()))) {
                        System.out.println("\t\tCallbacks: BLOCKED");
                    } else if (!checkerUsageStatusToCallbacks.isEmpty()) {
                        List<MethodOrMethodContext> callbacks = checkerUsageStatusToCallbacks.values().stream()
                                .collect(Collectors.toList());
                        System.out.println("\t\tCallback types: " + CallbackTypeUtil.getCallbackTypes(callbacks));
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

    private Map<Set<String>, LinkedHashSet<MethodOrMethodContext>> buildPermsToSensitivesMap() {
        return sensitives.stream().collect(
                Collectors.groupingBy(this::getPermissionsFor, Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * @return map from CheckerUsageStatus to callbacks with this status
     */
    private ListMultimap<CheckerUsageStatus, MethodOrMethodContext> getCheckerUsageStatusToCallbacksMap(
            Pair<Edge, Edge> checkerPair, String perm) {
        Set<MethodOrMethodContext> reachableCallbacks = checkerPathsHolder.getReachingCallbacks(checkerPair);
        if (reachableCallbacks == null) {
            reachableCallbacks = Collections.emptySet();
        }
        //noinspection Convert2MethodRef
        return reachableCallbacks.stream().sorted(SortUtil.methodOrMCComparator).collect(
                MyCollectors.toMultimapGroupingBy(
                        () -> ArrayListMultimap.create(),
                        callback -> perm != null ? getCheckUsageStatus(callback, perm) : CheckerUsageStatus.UNUSED));
    }

    public ContextSensOutflowCPHolder getSensitivePathsHolder() {
        return sensitivePathsHolder;
    }

    /**
     * All sensitives are printed one way or another.
     */
    private Set<Edge> getSensitiveEdgesInto() {
        CallGraph cg = Scene.v().getCallGraph();
        return sensitives.stream()
                .flatMap(meth -> StreamUtil.asStream(cg.edgesInto(meth)))
                .collect(Collectors.toSet());
    }

    /**
     * Computed from permsToCheckersMap
     */
    private Set<Edge> getPrintedCheckEdges() {
        return permsToCheckersMap.columnKeySet().stream().map(Pair::getO1).collect(Collectors.toSet());
    }

    public CheckerUsageStatus getCheckUsageStatus(MethodOrMethodContext callback, String perm) {
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

    public SetMultimap<MethodOrMethodContext, String> getCallbackToCheckedPermsMap() {
        return callbackToCheckedPermsMap;
    }

    public enum PermCheckStatus {
        CHECK_DETECTED, CHECK_NOT_DETECTED
    }
}

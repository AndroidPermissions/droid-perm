package org.oregonstate.droidperm.traversal;

import com.google.common.collect.*;
import com.sun.istack.internal.Nullable;
import org.oregonstate.droidperm.debug.DebugUtil;
import org.oregonstate.droidperm.jaxb.*;
import org.oregonstate.droidperm.scene.ClasspathFilter;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import org.oregonstate.droidperm.util.SortUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/19/2016.
 */
public class MethodPermDetector {

    private static final Logger logger = LoggerFactory.getLogger(MethodPermDetector.class);

    private File txtOut;
    private File xmlOut;
    private ScenePermissionDefService scenePermDef;

    private ClasspathFilter classpathFilter;

    public static final MethodOrMethodContext dummyMainMethod = getDummyMain();

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
    private LinkedHashSet<Edge> sensEdges;
    private LinkedHashSet<Edge> parametricSensEdges;

    private ContextSensOutflowCPHolder sensitivePathsHolder;

    private SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap;
    private Set<String> sometimesNotCheckedPerms;

    private JaxbCallbackList jaxbData;

    private static MethodOrMethodContext getDummyMain() {
        String sig = "<dummyMainClass: void dummyMainMethod(java.lang.String[])>";
        return CallGraphUtil.getEntryPointMethod(Scene.v().getMethod(sig));
    }

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
        permCheckers = CallGraphUtil.getNodesFor(scenePermDef.getPermCheckers());
        sensEdges = CallGraphUtil.getEdgesInto(scenePermDef.getSceneMethodSensitives()).stream()
                .sorted(SortUtil.edgeComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        parametricSensEdges = CallGraphUtil.getEdgesInto(scenePermDef.getSceneParametricSensitives()).stream()
                .sorted(SortUtil.edgeComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<MethodOrMethodContext> unifiedSensitives =
                Stream.concat(sensEdges.stream(), parametricSensEdges.stream()).distinct().map(Edge::getTgt)
                        .collect(Collectors.toSet());

        logger.info("Processing checkers");
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, permCheckers, classpathFilter);
        callbackToCheckedPermsMap = CheckerUtil.buildCallbackToCheckedPermsMap(checkerPathsHolder);
        permsToCheckersMap = CheckerUtil.buildPermsToCheckersMap(permCheckers);

        logger.info("Processing sensitives");
        sensitivePathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, unifiedSensitives, classpathFilter);
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
        UndetectedItemsUtil.printUndetectedMethSensitives(scenePermDef, sensEdges, classpathFilter);

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

    public Set<String> getPermissionsFor(Collection<Edge> sensEdges) {
        return sensEdges.stream().map(this::getPermissionsFor).collect(MyCollectors.toFlatSet());
    }

    /**
     * fixme full support for field sensitives
     * <p>
     * Right now adding only parametric methods
     */
    @Nullable
    public Set<String> getPermissionsFor(Edge sensEdge) {
        Set<String> methodSensPerm = scenePermDef.getPermissionsFor(sensEdge.tgt());
        if (!methodSensPerm.isEmpty()) {
            return methodSensPerm;
        }

        //Maybe this edge is a parametric sensitive.
        Stmt srcStmt = sensEdge.srcStmt();
        assert srcStmt != null; //sensitive edges always come from method calls
        Value arg0 = srcStmt.getInvokeExpr().getArg(0);
        if (arg0 instanceof StringConstant) {
            String argValue = ((StringConstant) arg0).value;
            return scenePermDef.getPermissionsFor(argValue);
        } else {
            throw new RuntimeException("Unsupported arg0 for parametric sensitive: " + arg0);
        }
    }

    private SetMultimap<MethodOrMethodContext, String> buildCallbackToRequiredPermsMap() {
        return sensitivePathsHolder.getSortedReachableCallbacks().stream().collect(MyCollectors.toMultimap(
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
        Multimap<Set<String>, Edge> permsToSensEdgesMap = buildPermsToSensEdgesMap();
        for (Set<String> permSet : permsToSensEdgesMap.keySet()) {
            System.out.println("\n" + permSet + "\n------------------------------------");

            Multimap<MethodOrMethodContext, Edge> sensToSensEdgesMap = permsToSensEdgesMap.get(permSet).stream()
                    .collect(MyCollectors.toMultimapGroupingBy(Edge::getTgt));
            Map<Set<PermCheckStatus>, Integer> sensitivesCountByStatus = new HashMap<>();
            for (MethodOrMethodContext sens : sensToSensEdgesMap.keySet()) {
                boolean printed = false;
                Iterable<Edge> edgesInto = sensToSensEdgesMap.get(sens);

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

                        ListMultimap<PermCheckStatus, MethodOrMethodContext> permCheckStatusToCallbacks =
                                getPermCheckStatusToCallbacksMap(edgeInto, permSet);
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

            //fixme proof of concept only for now
            Multimap<Set<String>, Edge> permsToParametricSensMap = buildPermsToParametricSensEdgesMap();
            System.out.println("Parametric sensitives: " + permsToParametricSensMap);
        }
    }

    private ListMultimap<PermCheckStatus, MethodOrMethodContext> getPermCheckStatusToCallbacksMap(
            Edge sensEdge, Set<String> permSet) {
        Set<MethodOrMethodContext> reachableCallbacks = sensitivePathsHolder.getReachingCallbacks(sensEdge);
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

    private Multimap<Set<String>, Edge> buildPermsToSensEdgesMap() {
        //noinspection Convert2MethodRef
        return sensEdges.stream().collect(
                MyCollectors.toMultimapGroupingBy(() -> LinkedHashMultimap.create(), this::getPermissionsFor));
    }

    private SetMultimap<Set<String>, Edge> buildPermsToParametricSensEdgesMap() {
        return parametricSensEdges.stream()
                .map(sensEdge -> new Pair<>(sensEdge, getPermissionsFor(sensEdge)))
                .filter(pair -> pair.getO2() != null)
                .collect(MyCollectors.toMultimapGroupingBy(Pair::getO2, Pair::getO1));
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

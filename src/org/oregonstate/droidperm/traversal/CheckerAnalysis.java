package org.oregonstate.droidperm.traversal;

import com.google.common.collect.*;
import org.oregonstate.droidperm.jaxb.CheckerUsageStatus;
import org.oregonstate.droidperm.scene.ClasspathFilter;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PointsToUtil;
import org.oregonstate.droidperm.util.PrintUtil;
import org.oregonstate.droidperm.util.SortUtil;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/7/2017.
 */
public class CheckerAnalysis {

    //fields received from MethodPermDetector
    private SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap;
    private ClasspathFilter classpathFilter;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private LinkedHashSet<Edge> checkerEdges;//for debug purposes
    private String captionSingular;
    private String captionPlural;

    //toperf holders for checkers and sensitives could be combined into one. One traversal could produce both.
    //this is actually required for flow-sensitive analysis.
    @SuppressWarnings("FieldCanBeLocal")
    private ContextSensOutflowCPHolder checkerPathsHolder;
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
    private Set<String> sometimesNotCheckedPerms;

    private SetMultimap<MethodOrMethodContext, String> callbackToCheckedPermsMap;

    public CheckerAnalysis(MethodOrMethodContext dummyMainMethod, LinkedHashSet<Edge> checkerEdges,
                           ClasspathFilter classpathFilter, CallGraphPermDefService cgService,
                           SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap,
                           String captionSingular, String captionPlural) {
        this.classpathFilter = classpathFilter;
        this.callbackToRequiredPermsMap = callbackToRequiredPermsMap;
        this.checkerEdges = checkerEdges;
        this.captionSingular = captionSingular;
        this.captionPlural = captionPlural;
        checkerPathsHolder = new ContextSensOutflowCPHolder(dummyMainMethod, checkerEdges, classpathFilter, cgService);
        callbackToCheckedPermsMap = buildCallbackToCheckedPermsMap(checkerPathsHolder);
        permsToCheckersMap = buildPermsToCheckersMap(checkerEdges);
        sometimesNotCheckedPerms = buildSometimesNotCheckedPerms(callbackToRequiredPermsMap);
    }

    /**
     * All checkers in context are printed here, including those that are not reachable from callbacks.
     * <p>
     * Map permsToCheckersMap may contain null keys. Also parent edge in pair maybe null.
     */
    public void printCheckersInContext(boolean printCallbacks) {
        String noCallbacksHeader = captionPlural + " in context in the call graph:";
        String callbacksHeader = captionPlural + " in context in the call graph, with reaching callbacks:";
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

                        System.out.println("\n" + captionSingular + " " + checkerMeth);
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
                        callback -> perm != null ? getCheckUsageStatus(callback, perm)
                                                 : CheckerUsageStatus.UNUSED));
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

    /**
     * Builds a table that maps (callback, permission) pairs to their usage status.
     */
    public Table<MethodOrMethodContext, String, CheckerUsageStatus> buildCheckerStatusMap() {
        Table<MethodOrMethodContext, String, CheckerUsageStatus> table = HashBasedTable.create();
        callbackToCheckedPermsMap.keySet().forEach(callback -> {
            Set<String> perms = callbackToCheckedPermsMap.get(callback);
            perms.forEach(
                    perm -> table.put(callback, perm, getCheckUsageStatus(callback, perm))
            );
        });
        return table;
    }

    private Set<String> buildSometimesNotCheckedPerms(
            SetMultimap<MethodOrMethodContext, String> callbackToRequiredPermsMap) {
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
    public PermCheckStatus getPermCheckStatusForAny(Set<String> permSet,
                                                    MethodOrMethodContext callback) {
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
    public PermCheckStatus getPermCheckStatusForAll(Collection<String> permSet,
                                                    MethodOrMethodContext callback) {
        if (callbackToCheckedPermsMap.get(callback) != null) {
            if (callbackToCheckedPermsMap.get(callback).containsAll(permSet)) {
                return PermCheckStatus.CHECK_DETECTED;
            }
        }
        return PermCheckStatus.CHECK_NOT_DETECTED;
    }

    public Set<Edge> getDetectedCheckerEdges() {
        return permsToCheckersMap.columnKeySet().stream().map(Pair::getO1).collect(Collectors.toSet());
    }

    private SetMultimap<MethodOrMethodContext, String> buildCallbackToCheckedPermsMap(
            ContextSensOutflowCPHolder checkerPathsHolder) {
        return checkerPathsHolder.getSortedReachableCallbacks().stream().collect(MyCollectors.toMultimap(
                HashMultimap::create,
                callback -> callback,
                //here getCallsToSensitiveFor actually means calls to checkers
                callback -> checkerPathsHolder.getCallsToSensitiveFor(callback).stream()
                        .flatMap((checkerCall) -> getPossiblePermissionsFromChecker(checkerCall,
                                checkerPathsHolder.getParentEdges(checkerCall, callback)).stream())
        ));
    }

    private Set<String> getPossiblePermissionsFromChecker(Edge checkerCall, Set<Edge> parents) {
        return parents.stream().flatMap(parent -> getPossiblePermissionsFromChecker(checkerCall, parent).stream())
                .collect(Collectors.toSet());
    }

    private Set<String> getPossiblePermissionsFromChecker(Edge checkerCall, Edge parent) {
        InvokeExpr invoke = checkerCall.srcStmt().getInvokeExpr();

        Value permArg = getPermArg(invoke);
        if (permArg instanceof StringConstant) {
            return Collections.singleton(((StringConstant) permArg).value);
        } else if (permArg instanceof Local) {
            PointsToAnalysis pta = Scene.v().getPointsToAnalysis();

            //supports arrays of strings as well
            PointsToSet pointsTo = PointsToUtil
                    .getPointsToWithFallback((Local) permArg, parent != null ? parent.srcStmt() : null, pta);
            if (pointsTo != null) {
                Set<String> permSet = pointsTo.possibleStringConstants();
                return permSet != null ? permSet : Collections.emptySet();
            } else {
                throw new RuntimeException("Null points-to for checker: " + invoke);
            }
        }
        throw new RuntimeException("Permission checker not supported for: " + invoke);
    }

    /**
     * key intuition: for checkers permission arg is of type String, for requesters - String[]
     */
    private Value getPermArg(InvokeExpr invoke) {
        List<Type> types = invoke.getMethodRef().parameterTypes();
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            if (type.toString().equals("java.lang.String") || (type instanceof ArrayType
                    && ((ArrayType) type).baseType.toString().equals("java.lang.String"))) {
                return invoke.getArg(i);
            }
        }
        throw new RuntimeException("No permission arg foudn for: " + invoke);
    }

    /**
     * row = permission, values = checkers that check this permission. If permission cannot be inferred,
     * value is empty string "".
     * <p>
     * column = Pair[Edge, ParentEdge]
     * value = whether check for this value on this path is certain or not.
     */
    private Table<String, Pair<Edge, Edge>, Boolean> buildPermsToCheckersMap(LinkedHashSet<Edge> checkerEdges) {
        CallGraph cg = Scene.v().getCallGraph();
        Table<String, Pair<Edge, Edge>, Boolean> table =
                Tables.newCustomTable(new TreeMap<>(), LinkedHashMap::new);
        for (Edge checkerEdge : checkerEdges) {
            Iterator<Edge> parentEdgesIt = cg.edgesInto(checkerEdge.getSrc());
            if (!parentEdgesIt.hasNext()) {
                table.put("", new Pair<>(checkerEdge, null), false);
                continue;
            }
            for (Edge parent : (Iterable<Edge>) () -> parentEdgesIt) {
                Set<String> possiblePerm = getPossiblePermissionsFromChecker(checkerEdge, parent);
                if (possiblePerm.isEmpty()) {
                    table.put("", new Pair<>(checkerEdge, parent), false);
                    continue;
                }
                for (String perm : possiblePerm) {
                    // if size == 1 then it's a certain check
                    table.put(perm, new Pair<>(checkerEdge, parent), possiblePerm.size() == 1);
                }
            }
        }
        return table;
    }

    public enum PermCheckStatus {
        CHECK_DETECTED, CHECK_NOT_DETECTED
    }
}

package org.oregonstate.droidperm.traversal;

import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/21/2016.
 */
public class CheckerUtil {
    private static final Logger logger = LoggerFactory.getLogger(CheckerUtil.class);

    static Map<MethodOrMethodContext, Set<String>> buildCallbackToCheckedPermsMap(
            ContextSensOutflowCPHolder checkerPathsHolder) {
        return checkerPathsHolder.getReachableCallbacks().stream().collect(Collectors.toMap(
                callback -> callback,
                //here getCallsToSensitiveFor actually means calls to checkers
                callback -> checkerPathsHolder.getCallsToSensitiveFor(callback).stream()
                        .map((checkerCall) -> getPossiblePermissionsFromChecker(checkerCall,
                                checkerPathsHolder.getParentEdges(checkerCall, callback)))
                        .collect(MyCollectors.toFlatSet())
        ));
    }

    static Set<String> getPossiblePermissionsFromChecker(Edge checkerCall, Set<Edge> parents) {
        return parents.stream().flatMap(parent -> getPossiblePermissionsFromChecker(checkerCall, parent).stream())
                .collect(Collectors.toSet());
    }

    static Set<String> getPossiblePermissionsFromChecker(Edge checkerCall, Edge parent) {
        InvokeExpr invoke = checkerCall.srcStmt().getInvokeExpr();

        //key intuition: in both perm. checker versions the last argument is the permission value
        Value lastArg = invoke.getArg(invoke.getArgCount() - 1);
        if (lastArg instanceof StringConstant) {
            return Collections.singleton(((StringConstant) lastArg).value);
        } else if (lastArg instanceof Local) {
            PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
            PointsToSet pointsTo = PointsToUtil
                    .getPointsToWithFallback((Local) lastArg, parent != null ? parent.srcStmt() : null, pta);
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
     * 1st level map: key = permission, values = checkers that check this permission.
     * <p>
     * 2nd level map: key = Pair[Edge, ParentEdge], value = whether certain or not.
     */
    public static Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> buildPermsToCheckersMap(
            Set<MethodOrMethodContext> permCheckers) {
        CallGraph cg = Scene.v().getCallGraph();
        Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> result = new HashMap<>();
        for (MethodOrMethodContext checker : permCheckers) {
            Iterable<Edge> edgesInto = IteratorUtil.asIterable(cg.edgesInto(checker));
            for (Edge edgeInto
                    : StreamUtil.asStream(edgesInto).sorted(SortUtil.edgeComparator).collect(Collectors.toList())) {
                Iterator<Edge> parentEdgesIt = cg.edgesInto(edgeInto.getSrc());
                if (!parentEdgesIt.hasNext()) {
                    checkAndPut(result, null, new Pair<>(edgeInto, null), false);
                    continue;
                }
                for (Edge parent : (Iterable<Edge>) () -> parentEdgesIt) {
                    Set<String> possiblePerm = getPossiblePermissionsFromChecker(edgeInto, parent);
                    if (possiblePerm.isEmpty()) {
                        checkAndPut(result, null, new Pair<>(edgeInto, parent), false);
                        continue;
                    }
                    for (String perm : possiblePerm) {
                        // if size == 1 then it's a certain check
                        checkAndPut(result, perm, new Pair<>(edgeInto, parent), possiblePerm.size() == 1);
                    }
                }
            }
        }
        return result;
    }

    private static void checkAndPut(Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> result,
                                    String perm, Pair<Edge, Edge> pair, boolean isCertain) {
        if (!result.containsKey(perm)) {
            result.put(perm, new LinkedHashMap<>());
        }
        result.get(perm).put(pair, isCertain);
    }
}
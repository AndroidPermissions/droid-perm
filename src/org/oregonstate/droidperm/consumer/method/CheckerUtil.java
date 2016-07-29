package org.oregonstate.droidperm.consumer.method;

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
            CallPathHolder checkerPathsHolder) {
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
            Set<String> permSet = pointsTo != null ? pointsTo.possibleStringConstants() : null;
            if (permSet != null) {
                if (permSet.size() != 1) {
                    logger.warn("Possible imprecision in the permission check: " + invoke + ", possible values: " +
                            permSet);
                }
                return permSet;
            }
        }
        logger.warn("Permission checkers not supported for: " + invoke);
        return Collections.emptySet();
    }

    /**
     * 1st level map: key = permission, values = checkers that check this permission.
     * <p>
     * 2nd level map: key = Pair[Edge, ParentEdge], value = whether cerain or not.
     */
    public static Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> buildPermsToCheckersMap(
            Set<MethodOrMethodContext> permCheckers) {
        CallGraph cg = Scene.v().getCallGraph();
        Map<String, LinkedHashMap<Pair<Edge, Edge>, Boolean>> result = new HashMap<>();
        for (MethodOrMethodContext checker : permCheckers) {
            Iterable<Edge> edgesInto = IteratorUtil.asIterable(cg.edgesInto(checker));
            for (Edge edgeInto
                    : StreamUtil.asStream(edgesInto).sorted(SortUtil.edgeComparator).collect(Collectors.toList())) {
                for (Edge parent : (Iterable<Edge>) () -> cg.edgesInto(edgeInto.getSrc())) {
                    Set<String> possiblePerm = getPossiblePermissionsFromChecker(edgeInto, parent);
                    for (String perm : possiblePerm) {
                        if (!result.containsKey(perm)) {
                            result.put(perm, new LinkedHashMap<>());
                        }
                        // if size == 1 then it's a safe check
                        result.get(perm).put(new Pair<>(edgeInto, parent), possiblePerm.size() == 1);
                    }
                }
            }
        }
        return result;
    }
}

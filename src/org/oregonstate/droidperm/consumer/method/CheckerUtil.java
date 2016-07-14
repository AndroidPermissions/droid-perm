package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.MyCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
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
                        .map(CheckerUtil::getPossiblePermissionsFromChecker).collect(MyCollectors.toFlatSet())
        ));
    }

    static Set<String> getPossiblePermissionsFromChecker(Edge checkerCall) {
        InvokeExpr invoke = checkerCall.srcStmt().getInvokeExpr();

        //key intuition: in both perm. checker versions the last argument is the permission value
        Value lastArg = invoke.getArg(invoke.getArgCount() - 1);
        if (lastArg instanceof StringConstant) {
            return Collections.singleton(((StringConstant) lastArg).value);
        } else if (lastArg instanceof Local) {
            PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
            //todo get points-to context-sensitively for GEOM, context-insens for SPARK.
            //Likely won't affect results in real apps anyway.
            Set<String> permSet = pta.reachingObjects((Local) lastArg).possibleStringConstants();
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
}

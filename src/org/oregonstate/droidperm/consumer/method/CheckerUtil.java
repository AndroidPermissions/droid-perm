package org.oregonstate.droidperm.consumer.method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.MethodOrMethodContext;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.Edge;

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
                        .map(CheckerUtil::getPermissionFromChecker).collect(Collectors.toSet())
        ));
    }

    static String getPermissionFromChecker(Edge checkerCall) {
        InvokeExpr invoke = checkerCall.srcStmt().getInvokeExpr();

        //key intuition: in both perm. checker versions the last argument is the permission value
        Value lastArg = invoke.getArg(invoke.getArgCount() - 1);
        if (lastArg instanceof StringConstant) {
            return ((StringConstant) lastArg).value;
        } else {
            logger.warn("Permission checkers with last argument non string literal are not supported: " + invoke);
            return null;
        }
    }
}

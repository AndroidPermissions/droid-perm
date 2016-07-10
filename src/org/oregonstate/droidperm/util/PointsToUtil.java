package org.oregonstate.droidperm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/9/2016.
 */
public class PointsToUtil {
    private static final Logger logger = LoggerFactory.getLogger(PointsToUtil.class);

    /**
     * Stmt with virtual calls could either be InvokeStmt or AssignStmt.
     *
     * @return points-to set for the call target, if virtual. Value null otherwise.
     */
    public static PointsToSet getPointsToIfVirtualCall(Unit unit, Context context, PointsToAnalysis pta) {
        // only virtual invocations require context sensitivity.
        InstanceInvokeExpr invoke = getVirtualInvokeIfPresent((Stmt) unit);
        return invoke != null ? getPointsTo(invoke, context, pta) : null;
    }

    public static InstanceInvokeExpr getVirtualInvokeIfPresent(Stmt stmt) {
        InvokeExpr invokeExpr = stmt.containsInvokeExpr() ? stmt.getInvokeExpr() : null;
        return invokeExpr != null
                       && (invokeExpr instanceof VirtualInvokeExpr || invokeExpr instanceof InterfaceInvokeExpr)
               ? (InstanceInvokeExpr) invokeExpr : null;
    }

    public static PointsToSet getPointsTo(InstanceInvokeExpr invoke, Context context, PointsToAnalysis pta) {
        //in Jimple target is always Local, regardless of who is the qualifier in Java.
        Local target = (Local) invoke.getBase();

        try {
            if (context != null) {
                return pta.reachingObjects(context, target);
            } else {
                //context == null means we are in a top-level method, which is still a valid option
                return pta.reachingObjects(target);
            }
        } catch (Exception e) { //happens for some JDK classes, probably due to geom-pta bugs.
            logger.debug(e.toString());
            return null;
        }
    }
}

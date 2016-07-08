package org.oregonstate.droidperm.consumer.method;

import soot.*;
import soot.jimple.InvokeStmt;

import java.util.Set;

/**
 * A version of ContextSensOutflowCPHolder that uses points-to to refine the call graph edges out of InvokeStmt, but not
 * for AssignStmt. When printing paths, points-to for both InvokeStmt and AssignStmt are printed.
 * <p>
 * Created for debugging the issues after points-to for AssignStmt was implemented.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/8/2016.
 */
public class PartialPointsToCPHolder extends ContextSensOutflowCPHolder {
    public PartialPointsToCPHolder(MethodOrMethodContext dummyMainMethod,
                                   Set<MethodOrMethodContext> sensitives,
                                   Set<SootMethod> outflowIgnoreSet) {
        super(dummyMainMethod, sensitives, outflowIgnoreSet);
    }

    @Override
    protected PointsToSet getPointsToForOutflows(Unit unit, Context context) {
        return unit instanceof InvokeStmt ? super.getPointsToForOutflows(unit, context) : null;
    }
}

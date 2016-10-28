package org.oregonstate.droidperm.traversal;

import org.oregonstate.droidperm.util.StreamUtil;
import soot.*;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/30/2016.
 */
public class TryCatchCheckerUtil {

    private static final SootClass SECURITY_EXCEPTION_CLS = Scene.v().getSootClass("java.lang.SecurityException");

    public static boolean isTryCatchChecked(Edge edge) {
        return getTryCatchCheckedStmts(edge.src()).contains(edge.srcStmt());
    }

    private static Set<Unit> getTryCatchCheckedStmts(SootMethod meth) {
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
        return meth.getActiveBody().getTraps().stream()
                .filter(trap -> hierarchy.isClassSuperclassOfIncluding(trap.getException(), SECURITY_EXCEPTION_CLS))
                .flatMap(trap -> StreamUtil.asStream(getTrappedUnits(trap, meth)))
                .collect(Collectors.toSet());
    }

    private static Iterable<Unit> getTrappedUnits(Trap trap, SootMethod meth) {
        if (trap.getBeginUnit() == trap.getEndUnit()) {
            //case when the trap has no elements;
            return Collections.emptyList();
        } else {
            Body body = meth.getActiveBody();
            //trap.getEndUnit() is the statement after the last trapped one. So we have to iterate one statement less.
            return () -> body.getUnits().iterator(trap.getBeginUnit(), body.getUnits().getPredOf(trap.getEndUnit()));
        }
    }
}

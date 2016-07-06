package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 3/28/2016.
 */
public class ContextSensOutflowCPHolder extends NoPointsToOutflowCPHolder {

    public ContextSensOutflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> sensitives,
                                      Set<SootMethod> outflowIgnoreSet) {
        super(dummyMainMethod, sensitives, outflowIgnoreSet);
    }

    /**
     * Iterator over outbound edges of a particular unit (likely method call).
     */
    protected Iterator<Edge> getUnitEdgeIterator(Unit unit, Context context, CallGraph cg) {
        InstanceInvokeExpr virtualInvoke = getVirtualInvokeIfPresent(unit);
        Iterator<Edge> edgesIterator = cg.edgesOutOf(unit);

        //Points-to is safe to compute only when there is at least one edge present.
        //Also checking for edges presence first is a performance improvement.
        if (edgesIterator.hasNext() && virtualInvoke != null && context != null) {
            PointsToSet pointsToSet = getPointsToIfVirtualCall(unit, context);
            if (pointsToSet == null) {
                //Computing points-to has thrown an exception. Disabling points-to for this unit.
                return super.getUnitEdgeIterator(unit, context, cg);
            }

            SootMethod staticTargetMethod = virtualInvoke.getMethod();
            Set<Type> pointsToTargetTypes = pointsToSet.possibleTypes();
            List<SootMethod> pointsToTargetMethods =
                    HierarchyUtil.resolveHybridDispatch(staticTargetMethod, pointsToTargetTypes);

            //todo: more precise support for fake edges - take into account the changed target.
            //Fake edges alter the natural mapping between edge.srcStmt() => edge.tgt()
            //  e.g. the actually invoked method is a different than the one allowed by class hierarchy.
            //Problems with fake edges:
            // 1. They alter the invoked method. Ex: Thread.start() => Thread.run().
            //      edge.srcStmt()...getMethod() != edge.tgt()
            // 2. They might alter invocation target. Ex: executor.submit(r) => r.run()
            //      edge.srcStmt()...getBase()
            //          != actual receiver inside OFCGB.methodToReceivers.get(edge.srcStmt()...getMethod())
            //      How to get it???
            //      v1: Get it correctly from OnFlyCallGraphBuilder.
            //      v2: Hack it for every particular implementation of fake edge.

            //Why context sensitivity works for Thread.start()?
            //  Current algorithm won't distinguish between 2 statements Thread.start() within the same method,
            //  but it doesn't matter for the purpose of DroidPerm.
            //Context sensitivity for Thread is actually achieved by cleaning up unfeasible edges in GeomPointsTo,
            //  not through PointsToSet analysis in this class.
            //todo: write a new version of CSens Outflow that doesn't use PointsTo data, reuse the code.
            return StreamUtil.asStream(edgesIterator)
                    .filter(edge -> edgeMatchesPointsTo(edge, pointsToTargetMethods))
                    .iterator();
        }

        //default case, anything except virtual method calls
        return super.getUnitEdgeIterator(unit, context, cg);
    }

    private boolean edgeMatchesPointsTo(Edge edge, List<SootMethod> pointsToTargetMethods) {
        //     This is the main case: real edges
        return pointsToTargetMethods.contains(edge.getTgt().method())
                //2nd case: fake edges
                //Fake edges are a hack in Soot for handling async constructs.
                //If it's a fake edge, include it without comparing to actual targets.
                //With one exception: ExecutorService.execute(). That one is handled better by crafted classpath.
                || (edge.kind().isFake());
                /*Limitation: cannot disable fake edges kinf EXECUTOR, because it would require crafting
                a custom executor.execute() for every implementation of ExecutorService. Those fake edges are still
                needed when executor.execute() is called directly by the app.

                 The only drawback is a bit uglier paths, due to fake edge being logged instead of a nice crafted one.
                */
    }

}

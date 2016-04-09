package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import soot.*;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.toolkits.callgraph.*;
import soot.util.MultiMap;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/22/2016.
 */
public class DebugUtil {
    public static void printTransitiveTargets(MethodOrMethodContext meth) {
        System.out.println("\nTransitive targets for " + meth);
        StreamUtil.asStream(new TransitiveTargets(Scene.v().getCallGraph()).iterator(meth))
                .forEach(tgt -> System.out.println("  " + tgt));
    }

    public static void printTransitiveTargets(Set<MethodOrMethodContext> consumers) {
        System.out.println("\n\nTransitive targets for each consumer \n====================================");
        consumers.stream().forEach(DebugUtil::printTransitiveTargets);
    }

    public static void printTargets(MethodOrMethodContext meth) {
        System.out.println("\nDirect targets for " + meth);
        StreamUtil.asStream(new Targets(Scene.v().getCallGraph().edgesOutOf(meth)))
                .forEach(tgt -> System.out.println("  " + tgt));
    }

    public static void printTargets(Set<MethodOrMethodContext> consumers) {
        System.out.println("\n\nDirect targets for each consumer \n====================================");
        consumers.stream().forEach(DebugUtil::printTargets);
    }

    public static void pointsToTest() {
        SootClass threadClass = Scene.v().getSootClass("java.lang.Thread");
        SootMethod threadStart = Scene.v().grabMethod("<java.lang.Thread: void start()>");
        SootField threadTarget =
                threadClass.getFieldByName("target0"); // this is the actual field name in patched code by Steven.
        CallGraph cg = Scene.v().getCallGraph();
        PointsToAnalysis pointsTo = Scene.v().getPointsToAnalysis();
        List<Edge> edges = StreamUtil.asStream(cg.edgesInto(threadStart)).collect(Collectors.toList());
        for (Edge edge : edges) {
            MethodOrMethodContext context = edge.getSrc();
            JInvokeStmt srcStmt = (JInvokeStmt) edge.srcStmt();
            Value threadExpr = ((VirtualInvokeExpr) srcStmt.getInvokeExpr()).getBase();
            if (threadExpr instanceof Local) {
                Local threadLocal = (Local) threadExpr;
                PointsToSet reachingObjects = pointsTo.reachingObjects(threadLocal, threadTarget);
                System.out.println("Inside " + context);
                System.out.println(threadLocal.getName() + "." + threadTarget.getName() + ": " + reachingObjects);
                System.out.println();
            }
        }
    }

    public static void printCallGraph() {
        try {
            ContextAwareCallGraph cg = (ContextAwareCallGraph) Scene.v().getCallGraph();
            System.out.println("\n\n\n\n\n\n\nMethod to MethodContext:");
            System.out.println("=============================\n");
            Field field = ContextAwareCallGraph.class.getDeclaredField("methodToMethodContext");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            MultiMap<SootMethod, MethodOrMethodContext> methodToMethodContext =
                    (MultiMap<SootMethod, MethodOrMethodContext>) field.get(cg);
            for (SootMethod meth : methodToMethodContext.keySet()) {
                System.out.println("\n" + meth);
                System.out.println("--------------------------");
                for (MethodOrMethodContext mc : methodToMethodContext.get(meth)) {
                    System.out.println("    " + mc.context());
                    System.out.println("    Edges into:");
                    cg.edgesInto(mc).forEachRemaining(edge -> System.out.println("        " + edge));
                    System.out.println("    Edges out of:");
                    cg.edgesOutOf(mc).forEachRemaining(edge -> System.out.println("        " + edge));
                }
            }

            System.out.println("\n\n\n\n\n\n\nAll edges:");
            System.out.println("=============================\n");
            cg.iterator().forEachRemaining(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.consumer.method.CallPathHolder;
import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Targets;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.util.MultiMap;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/22/2016.
 */
public class DebugUtil {
    private static final Logger logger = LoggerFactory.getLogger(DebugUtil.class);

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

    public static void debugEdgesOutOf(CallGraph cg, MethodOrMethodContext node) {
        if (node.toString()
                .equals("<java.util.concurrent.AbstractExecutorService:" +
                        " java.util.concurrent.Future submit(java.lang.Runnable)>")) {
            System.out.println("Edges out of AbstractExecutorService.submit():");
            cg.edgesOutOf(node).forEachRemaining(edge -> System.out.println("    " + edge));
        }
        if (node.toString()
                .equals("<java.lang.Thread: void run()>")) {
            System.out.println("Edges out of Thread.run():");
            cg.edgesOutOf(node).forEachRemaining(edge -> System.out.println("    " + edge));
        }
    }

    /**
     * For the given pair sensitive-callback, print all locations that call the sensitive. For each location, print
     * class name and line number.
     */
    public static void printCallClassesAndLineNumbers(MethodOrMethodContext sensitive, MethodOrMethodContext callback,
                                                      CallPathHolder sensitivePathsHolder) {
        System.out.println("        Sensitive calls: ");
        //from bytecode we can only get line numbers, not column numbers.
        //noinspection ConstantConditions
        sensitivePathsHolder.getCallsToMeth(sensitive, callback).forEach(edge ->
                System.out.println("        " + edge.src().getDeclaringClass() + ": "
                        + edge.srcStmt().getJavaSourceStartLineNumber())
        );
    }

    public static void dumpCallGraph(File file) {
        logger.info("Dumping call graph to " + file);
        long time = System.currentTimeMillis();

        try {
            CallGraph cg = Scene.v().getCallGraph();
            PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
            Map<MethodOrMethodContext, Edge> srcMethodToEdge;
            Field srcMethodToEdgeField = CallGraph.class.getDeclaredField("srcMethodToEdge");
            srcMethodToEdgeField.setAccessible(true);
            //noinspection unchecked
            srcMethodToEdge = (Map<MethodOrMethodContext, Edge>) srcMethodToEdgeField.get(cg);
            PrintWriter writer = new PrintWriter(new FileWriter(file));

            for (MethodOrMethodContext method : srcMethodToEdge.keySet()) {
                writer.println("From " + method);
                cg.edgesOutOf(method).forEachRemaining(edge -> writer.println("\tTo " + edge.tgt()));
                writer.println();
                writer.println("\tPoints-to:");

                method.method().getActiveBody().getUnits().stream().filter(unit -> unit instanceof DefinitionStmt)
                        .forEach(u -> {
                            DefinitionStmt assign = (DefinitionStmt) u;
                            Value leftOp = assign.getLeftOp();
                            if (leftOp instanceof Local) {
                                PointsToSet pointsTo;
                                try {
                                    pointsTo = pta.reachingObjects((Local) leftOp);
                                } catch (Exception e) {
                                    pointsTo = null;
                                }
                                writer.println("\t\t" + assign + ": " +
                                        (pointsTo != null ? pointsTo.possibleTypes() : "exception"));
                            }
                        });
                writer.println();
            }
            writer.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            logger.info("Call graph dumped in " + (System.currentTimeMillis() - time) / 1000.0 + " sec");
        }
    }
}

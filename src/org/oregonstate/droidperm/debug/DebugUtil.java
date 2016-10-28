package org.oregonstate.droidperm.debug;

import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import org.oregonstate.droidperm.util.PointsToUtil;
import org.oregonstate.droidperm.util.SortUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;
import soot.util.MultiMap;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/22/2016.
 */
public class DebugUtil {
    private static final Logger logger = LoggerFactory.getLogger(DebugUtil.class);

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
            Value threadExpr = ((VirtualInvokeExpr) edge.srcStmt().getInvokeExpr()).getBase();
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

    public static void printBody(String methodSig) {
        SootMethod meth = Scene.v().getMethod(methodSig);

        System.out.println("Units:");
        Body body = meth.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        units.forEach(unit -> System.out.println("\t" + unit + " : " + unit.getJavaSourceStartLineNumber()));
        System.out.println();

        System.out.println("Traps:");
        Chain<Trap> traps = body.getTraps();
        traps.forEach(trap -> {
            System.out.println("\tTrap :"
                    + "\n\t\tbegin: " + trap.getBeginUnit() + " : " + trap.getBeginUnit()
                    .getJavaSourceStartLineNumber()
                    + "\n\t\tend: " + trap.getEndUnit() + " : " + trap.getEndUnit().getJavaSourceStartLineNumber()
                    + "\n\t\thandler: "
                    + trap.getHandlerUnit() + " : " + trap.getHandlerUnit().getJavaSourceStartLineNumber()
                    + "\n\t\tex type: " + trap.getException() + "\n");
        });

        System.out.println();
    }

    public static Unit getUnitInBody(Unit unit, Body body) {
        return body.getUnits().stream().filter(bodyUnit -> bodyUnit.equals(unit)).findAny().orElse(null);
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
     * For each class, dump points-to of all fields that are of reference type. For Each method in the call graph, dump
     * all local variables, all the fields in @this, and all the outgoing methods. Calls to class initializer (clinit)
     * are not printed.
     * <p>
     * For statements that have outgoing edges, an invocation, and empty or null points-to, we have a case of points-to
     * inconsistency. For them a special message "EMPTY/NULL POINTS-TO" is printed.
     *
     * @param file The output file.
     */
    public static void dumpPointsToAndCallGraph(File file) {
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

            dumpPointsTo(pta, srcMethodToEdge, writer);
            writer.println("\n\n\n======================================");
            dumpCallGraph(cg, pta, srcMethodToEdge, writer);
            writer.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            logger.info("Call graph dumped in " + (System.currentTimeMillis() - time) / 1000.0 + " sec");
        }
    }

    private static void dumpPointsTo(PointsToAnalysis pta,
                                     Map<MethodOrMethodContext, Edge> srcMethodToEdge, PrintWriter writer) {
        Stream<SootClass> classes = srcMethodToEdge.keySet().stream().map(meth -> meth.method().getDeclaringClass())
                .distinct().sorted(Comparator.comparing(SootClass::getName));
        classes.forEach(clazz -> {
            writer.println("Points-to for " + clazz);
            for (SootField field : clazz.getFields()) {
                if (field.isStatic() && field.getType() instanceof RefLikeType) {
                    PointsToSet pointsTo = null;
                    Exception pte = null;
                    try {
                        pointsTo = pta.reachingObjects(field);
                    } catch (Exception e) {
                        pte = e;
                    }
                    writer.println("\t" + field + ": " + toDisplayString(pointsTo, pte));
                }
            }
        });
    }

    private static void dumpCallGraph(CallGraph cg, PointsToAnalysis pta,
                                      Map<MethodOrMethodContext, Edge> srcMethodToEdge, PrintWriter writer) {
        for (MethodOrMethodContext method : srcMethodToEdge.keySet()) {
            writer.println("From " + method);
            dumpMethodEdges(method, cg, writer);
            writer.println();
            writer.println("\tPoints-to:");

            dumpMethodPointsTo(method, pta, writer);
            writer.println();
        }
    }

    private static void dumpMethodEdges(MethodOrMethodContext method, CallGraph cg, PrintWriter writer) {
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
        for (Unit unit : method.method().getActiveBody().getUnits()) {
            Iterator<Edge> edges = cg.edgesOutOf(unit);
            if (edges.hasNext()) {
                boolean unitPrinted = false;
                while (edges.hasNext()) {
                    Edge edge = edges.next();
                    if (edge.tgt().getName().contains("<clinit>")) {
                        continue;
                    }

                    if (!unitPrinted) {
                        writer.println("\t" + unit);
                        unitPrinted = true;

                        //Check if this unit is an invocation with empty points-to. If yes, print.
                        if (PointsToUtil.getVirtualInvokeIfPresent((Stmt) unit) != null) {
                            PointsToSet pointsTo = PointsToUtil.getPointsToIfVirtualCall(unit, null, pta);
                            if (pointsTo == null) {
                                writer.println("\t\t\t\tNULL POINTS-TO!!!");
                            } else if (pointsTo.possibleTypes().isEmpty()) {
                                writer.println("\t\t\t\tEMPTY POINTS-TO!!!");
                            }
                        }
                    }
                    writer.println("\t\tTo " + edge.tgt());
                }
            }
        }
    }

    private static void dumpMethodPointsTo(MethodOrMethodContext method, PointsToAnalysis pta, PrintWriter writer) {
        for (Unit unit : method.method().getActiveBody().getUnits()) {
            if (unit instanceof DefinitionStmt) {
                DefinitionStmt assign = (DefinitionStmt) unit;
                Value leftOp = assign.getLeftOp();
                if (leftOp instanceof Local && leftOp.getType() instanceof RefLikeType) {
                    PointsToSet pointsTo = null;
                    Exception pte = null;
                    try {
                        pointsTo = pta.reachingObjects((Local) leftOp);
                    } catch (Exception e) {
                        pte = e;
                    }
                    writer.println("\t\t" + assign + ": " + toDisplayString(pointsTo, pte));

                    //Dump instance fields of @this
                    if (assign.getRightOp() instanceof ThisRef) {
                        SootClass thisClass = ((RefType) leftOp.getType()).getSootClass();
                        for (SootField field : thisClass.getFields()) {
                            if (!field.isStatic() && field.getType() instanceof RefLikeType) {
                                PointsToSet fieldPointsTo = null;
                                Exception fpte = null;
                                try {
                                    fieldPointsTo = pta.reachingObjects((Local) leftOp, field);
                                } catch (Exception e) {
                                    fpte = e;
                                }
                                writer.println("\t\t\tthis.<" + field.getSubSignature()
                                        + ">: " + toDisplayString(fieldPointsTo, fpte));
                            }
                        }
                    }
                }
            }
        }
    }

    private static String toDisplayString(PointsToSet pointsTo, Exception e) {
        return "" + (pointsTo != null ? pointsTo.possibleTypes() : "EXCEPTION: " + e);
    }

    public static void logClassesWithCallbacks(Set<MethodOrMethodContext> uiCallbacks) {
        Set<SootClass> callbackClasses = getCallbackClasses(uiCallbacks);
        System.out.println("\nTotal classes with callbacks: " + callbackClasses.size() + "\n"
                + "========================================================================");
        callbackClasses.forEach(System.out::println);
        System.out.println();
    }

    private static Set<SootClass> getCallbackClasses(Set<MethodOrMethodContext> uiCallbacks) {
        return uiCallbacks.stream()
                //classes that only have constructors and static initializers as "callbacks" are filtered out.
                .filter(meth -> !(meth.method().isConstructor() || meth.method().isStaticInitializer()))

                .map(meth -> meth.method().getDeclaringClass())
                .sorted(SortUtil.classComparator).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static void logFragments(Set<MethodOrMethodContext> uiCallbacks) {
        List<SootClass> detectedFragments = getFragmentsFrom(getCallbackClasses(uiCallbacks));
        List<SootClass> undetectedFragments = getFragmentsFrom(Scene.v().getApplicationClasses());
        undetectedFragments.removeAll(detectedFragments);

        System.out.println("\nFragments\n"
                + "========================================================================");
        System.out.println("Total fragments: " + (detectedFragments.size() + undetectedFragments.size()));
        System.out.println("Detected fragments: " + detectedFragments.size());
        System.out.println("Undetected fragments: " + undetectedFragments.size());

        System.out.println("\nDetected fragments: " + detectedFragments.size() + "\n"
                + "------------------------------------------------------------------------");
        detectedFragments.stream().sorted(SortUtil.classComparator).forEach(System.out::println);

        System.out.println("\nUndetected fragments: " + undetectedFragments.size() + "\n"
                + "------------------------------------------------------------------------");
        undetectedFragments.stream().sorted(SortUtil.classComparator).forEach(System.out::println);
    }

    private static List<SootClass> getFragmentsFrom(Collection<SootClass> inputClasses) {
        return inputClasses.stream()
                .filter(sootClass ->
                        sootClass.isConcrete()
                                && !(sootClass.getName().startsWith("android.")
                                || sootClass.getName().startsWith("java."))
                                && isFragment(sootClass)
                )
                .collect(Collectors.toList());
    }

    private static boolean isFragment(SootClass sootClass) {
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
        List<SootClass> superclasses = hierarchy.getSuperclassesOf(sootClass);
        SootClass fragmentClass = Scene.v().getSootClassUnsafe("android.app.Fragment");
        SootClass supportFragmentClass = Scene.v().getSootClassUnsafe("android.support.v4.app.Fragment");
        return superclasses.contains(fragmentClass) || superclasses.contains(supportFragmentClass);
    }
}

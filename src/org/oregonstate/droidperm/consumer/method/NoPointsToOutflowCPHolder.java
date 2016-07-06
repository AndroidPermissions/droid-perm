package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.geom.geomPA.GeomPointsTo;
import soot.jimple.spark.ondemand.genericutil.HashSetMultiMap;
import soot.jimple.spark.ondemand.genericutil.MultiMap;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/6/2016.
 */
public class NoPointsToOutflowCPHolder extends AbstractCallPathHolder {

    private static final Logger logger = LoggerFactory.getLogger(ContextSensOutflowCPHolder.class);
    private final PointsToAnalysis pointsToAnalysis;

    /**
     * Methods ignored by the outflow algorithm
     */
    private Set<SootMethod> outflowIgnoreSet;

    private long time = System.currentTimeMillis();

    private Set<MethodInContext> sensitivesInContext = new HashSet<>();

    //todo investigate using a true call graph for outflows, instead of these maps.
    //Potentially will improve performance, since callbackToOutflowMap contains a lot of repetition.
    //advantage: efficient navigation both upwards and downwards.
    // Could use MethodOrMethodContext, or downright MethodContext
    // to distinguish between edges in the Soot CG and edges in my outflow CG.
    /**
     * Map from UI callbacks to their outflows, as breadth-first trees in the call graph.
     * <p>
     * 1-st level map: key = callback, value = outflow of that callback.
     * <p>
     * 2-nd level map: key = node in the outflow, value = parent node. Both are context-sensitive.
     */
    private Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> callbackToOutflowMap;

    /**
     * From each MethodInContext in the call graph, the set of sensitives it reaches.
     */
    private MultiMap<MethodInContext, MethodOrMethodContext> reachableSensitives;

    /**
     * Map from sensitives to sets of callbacks.
     */
    private Map<MethodInContext, Set<MethodOrMethodContext>> sensitiveInCToCallbacksMap;

    public NoPointsToOutflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> sensitives,
                                     Set<SootMethod> outflowIgnoreSet) {
        super(dummyMainMethod, sensitives);
        this.outflowIgnoreSet = outflowIgnoreSet;

        pointsToAnalysis = Scene.v().getPointsToAnalysis();
        if (pointsToAnalysis.getClass() != GeomPointsTo.class) {
            logger.warn("ContextSensOutflowCPHolder is slow with PointsTo algorithms other than GEOM");
        }

        callbackToOutflowMap = buildCallbackToOutflowMap();
        sensitiveInCToCallbacksMap = buildSensitiveToCallbacksMap();
        buildReachableSensitives();
    }

    private Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> buildCallbackToOutflowMap() {
        Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> map = new HashMap<>();
        for (MethodOrMethodContext callback : getUICallbacks()) {
            Map<MethodInContext, MethodInContext> outflow = getBreadthFirstOutflow(callback);

            Collection<MethodOrMethodContext> outflowNodes =
                    outflow.keySet().stream().map(methIC -> methIC.method).collect(Collectors.toList());
            if (!Collections.disjoint(outflowNodes, sensitives)) {
                map.put(callback, outflow);
            }

            long newTime = System.currentTimeMillis();
            logger.info("DP: Callback processed: " + callback + " in " + (newTime - time) / 1E3 + " sec");
            time = newTime;
        }
        return map;
    }

    private Set<MethodOrMethodContext> getUICallbacks() {
        return StreamUtil.asStream(Scene.v().getCallGraph().edgesOutOf(dummyMainMethod))
                .map(Edge::getTgt).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Produces the outflow tree starting from the root method, by breadth-first traversal.
     *
     * @return A map from nodes in the outflow to their parent.
     */
    private Map<MethodInContext, MethodInContext> getBreadthFirstOutflow(MethodOrMethodContext root) {
        CallGraph cg = Scene.v().getCallGraph();
        Queue<MethodInContext> queue = new ArrayDeque<>();
        Set<MethodInContext> traversed = new HashSet<>();

        Map<MethodInContext, MethodInContext> outflow = new HashMap<>();
        MethodInContext rootInContext = new MethodInContext(root);
        queue.add(rootInContext);
        traversed.add(rootInContext);


        for (MethodInContext meth = queue.poll(); meth != null; meth = queue.poll()) {
            final MethodInContext srcInContext = meth; //to make lambda expressions happy
            MethodOrMethodContext srcMeth = srcInContext.method;
            if (srcMeth.method().hasActiveBody() &&
                    //do not pass through methods in the ignore list
                    !outflowIgnoreSet.contains(srcMeth.method())) {
                srcMeth.method().getActiveBody().getUnits().stream().forEach(
                        (Unit unit) -> getUnitEdgeIterator(unit, srcInContext.getContext(), cg)
                                .forEachRemaining((Edge edge) -> {
                                    MethodInContext tgtInContext = new MethodInContext(edge);

                                    if (!traversed.contains(tgtInContext)) {
                                        traversed.add(tgtInContext);
                                        queue.add(tgtInContext);
                                        outflow.put(tgtInContext, srcInContext);
                                        if (sensitives.contains(edge.getTgt())) {
                                            sensitivesInContext.add(tgtInContext);
                                        }
                                    }
                                }));
            }
        }
        return outflow;
    }

    /**
     * Iterator over outbound edges of a particular unit (likely method call).
     */
    protected Iterator<Edge> getUnitEdgeIterator(Unit unit, Context context, CallGraph cg) {
        return cg.edgesOutOf(unit);
    }

    private Map<MethodInContext, Set<MethodOrMethodContext>> buildSensitiveToCallbacksMap() {
        return sensitivesInContext.stream().collect(Collectors.toMap(
                sensitiveInContext -> sensitiveInContext,
                sensitiveInContext -> callbackToOutflowMap.entrySet().stream()
                        .filter(cbToOutflowEntry -> cbToOutflowEntry.getValue().containsKey(sensitiveInContext))
                        .map(Map.Entry::getKey).collect(Collectors.toSet())
        ));
    }

    private void buildReachableSensitives() {
        reachableSensitives = new HashSetMultiMap<>();
        for (MethodOrMethodContext callback : callbackToOutflowMap.keySet()) {
            for (MethodInContext sensitiveInContext : sensitivesInContext) {
                if (callbackToOutflowMap.get(callback).containsKey(sensitiveInContext)) {
                    collectReachableSensitivesOnPath(callback, sensitiveInContext, callbackToOutflowMap.get(callback));
                }
            }
        }
    }

    private void collectReachableSensitivesOnPath(MethodOrMethodContext src, MethodInContext sensitiveInContext,
                                                  Map<MethodInContext, MethodInContext> outflow) {
        MethodInContext node = sensitiveInContext;
        while (node != null && node.method != src) {
            reachableSensitives.put(node, sensitiveInContext.method);
            node = outflow.get(node);
        }
        if (node != null) {
            reachableSensitives.put(node, sensitiveInContext.method);
        }
    }

    @Override
    public void printPathsFromCallbackToSensitive() {
        System.out.println("\nPaths from each callback to each sensitive");
        System.out.println("============================================\n");


        for (MethodOrMethodContext callback : callbackToOutflowMap.keySet()) {
            for (MethodInContext sensitiveInContext : sensitivesInContext) {
                if (callbackToOutflowMap.get(callback).containsKey(sensitiveInContext)) {
                    printPath(callback, sensitiveInContext, callbackToOutflowMap.get(callback));
                }
            }
        }
    }

    private void printPath(MethodOrMethodContext src, MethodInContext dest,
                           Map<MethodInContext, MethodInContext> outflow) {
        List<MethodInContext> path = computePathFromOutflow(src, dest, outflow);

        System.out.println("From " + src + "\n  to " + dest);
        System.out.println("----------------------------------------------------------------------------------------");
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                MethodInContext methodInC = path.get(i);
                MethodInContext child = i < path.size() - 1 ? path.get(i + 1) : null;
                System.out.println(methodInC != null ? pathNodeToString(methodInC, child) : null);
            }
            System.out.println();
        } else {
            System.out.println("Not found!");
        }
        System.out.println();
    }

    /**
     * @param methodInC currently printed method
     * @param child     child of methodInC
     * @return string to print representing methodInC
     */
    private String pathNodeToString(MethodInContext methodInC, MethodInContext child) {
        StringBuilder out = new StringBuilder();

        //parent method
        out.append(methodInC.method);

        //invocation line number in parent method
        if (child != null && child.getContext() != null) {
            out.append(" : ").append(((Stmt) child.getContext()).getJavaSourceStartLineNumber());
        }

        //points to of the invocation target
        boolean printPointsTo = child != null && getVirtualInvokeIfPresent((Stmt) child.getContext()) != null;
        if (printPointsTo) {
            PointsToSet pointsTo = getPointsToIfVirtualCall((Stmt) child.getContext(), methodInC.getContext());
            out.append("\n                                                                p-to: ");
            if (pointsTo != null) {
                out.append(pointsTo.possibleTypes().stream()
                        .map(type -> type.toString().substring(type.toString().lastIndexOf(".") + 1))
                        .collect(Collectors.toList()));
            } else {
                out.append("exception");
            }
        }

        //shortcutted call, if it's a fake edge
        if (child != null && child.edge.kind().isFake()) { //child edge is always != null
            out.append("\n    ");
            out.append(getInvokedMethod((InvokeStmt) child.getContext()));
            out.append(
                    "\n                                                                FAKE edge: call shortcutted");
        }

        return out.toString();
    }

    private List<MethodInContext> computePathFromOutflow(MethodOrMethodContext src, MethodInContext dest,
                                                         Map<MethodInContext, MethodInContext> outflow) {
        List<MethodInContext> path = new ArrayList<>();
        MethodInContext node = dest;
        while (node != null && node.method != src) {
            path.add(node);
            node = outflow.get(node);
        }
        path.add(node != null ? node : null);
        Collections.reverse(path);
        return path;
    }

    /**
     * Stmt with virtual calls could either be InvokeStmt or AssignStmt.
     *
     * @return points-to set for the call target, if virtual. Value null otherwise.
     */
    protected PointsToSet getPointsToIfVirtualCall(Unit unit, Context context) {
        // only virtual invocations require context sensitivity.
        InstanceInvokeExpr invoke = getVirtualInvokeIfPresent(unit);

        if (invoke != null) {
            //in Jimple target is always Local, regardless of who is the qualifier in Java.
            Local target = (Local) invoke.getBase();

            try {
                if (context != null) {
                    return pointsToAnalysis.reachingObjects(context, target);
                } else {
                    //context == null means we are in a top-level method, which is still a valid option
                    return pointsToAnalysis.reachingObjects(target);
                }
            } catch (Exception e) { //happens for some JDK classes, probably due to geom-pta bugs.
                logger.debug(e.toString());
                return null;
            }
        }
        return null;
    }

    protected InstanceInvokeExpr getVirtualInvokeIfPresent(Unit unit) {
        Value possibleInvokeExpr = null;
        if (unit instanceof InvokeStmt) {
            possibleInvokeExpr = ((InvokeStmt) unit).getInvokeExpr();
        } else if (unit instanceof AssignStmt) {
            possibleInvokeExpr = ((AssignStmt) unit).getRightOp();
        }
        return possibleInvokeExpr != null
                       && (possibleInvokeExpr instanceof VirtualInvokeExpr
                || possibleInvokeExpr instanceof InterfaceInvokeExpr)
               ? (InstanceInvokeExpr) possibleInvokeExpr : null;
    }

    private static SootMethod getInvokedMethod(InvokeStmt stmt) {
        return stmt.getInvokeExpr().getMethod();
    }

    @Override
    public Set<MethodOrMethodContext> getReacheableSensitives(Edge edge) {
        return reachableSensitives.get(new MethodInContext(edge));
    }

    @Override
    public Set<Edge> getCallsToSensitiveFor(MethodOrMethodContext callback) {
        //toperf cg.findEdge() is potentially expensive. Better keep edges in the outflow.
        CallGraph cg = Scene.v().getCallGraph();
        return callbackToOutflowMap.get(callback).keySet().stream().filter(sensitivesInContext::contains)
                .map(methInCt -> cg.findEdge((Unit) methInCt.getContext(), methInCt.method.method()))
                .collect(Collectors.toSet());
    }

    @Override
    public List<Edge> getCallsToMeth(MethodOrMethodContext meth, MethodOrMethodContext callback) {
        CallGraph cg = Scene.v().getCallGraph();
        return callbackToOutflowMap.get(callback).keySet().stream().filter(methInC -> methInC.method == meth)
                .map(methInCt -> cg.findEdge((Unit) methInCt.getContext(), methInCt.method.method()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap() {
        return sensitiveInCToCallbacksMap.keySet().stream().collect(Collectors.toMap(
                sensInC -> sensInC.method,
                sensInC -> sensitiveInCToCallbacksMap.get(sensInC),
                StreamUtil::newObjectUnion
        ));
    }

    public Map<MethodInContext, Set<MethodOrMethodContext>> getSensitiveInCToCallbacksMap() {
        return sensitiveInCToCallbacksMap;
    }
}

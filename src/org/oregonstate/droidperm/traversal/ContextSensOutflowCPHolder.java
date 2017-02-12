package org.oregonstate.droidperm.traversal;

import com.google.common.collect.*;
import org.oregonstate.droidperm.scene.ClasspathFilter;
import org.oregonstate.droidperm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.geom.geomPA.GeomPointsTo;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 3/28/2016.
 */
public class ContextSensOutflowCPHolder {

    private static final Logger logger = LoggerFactory.getLogger(ContextSensOutflowCPHolder.class);

    protected MethodOrMethodContext dummyMainMethod;
    protected Set<Edge> initSensEdges;
    /**
     * Methods ignored by the outflow algorithm
     */
    private ClasspathFilter classpathFilter;
    private final CallGraphPermDefService cgService;

    protected final PointsToAnalysis pointsToAnalysis;
    protected final CallGraph callGraph = Scene.v().getCallGraph();

    private long time = System.currentTimeMillis();

    /**
     * BiMap from callback methods to edges that call those methods. For each callback method there's only one
     * corresponding edge - the first.
     * <p>
     * Contains all callbacks, not only those having sensitives.
     */
    private BiMap<MethodOrMethodContext, Edge> uiCallbacksBiMap;

    /**
     * Sensitives actually traversed by the outflow algorithm. A sensitive my be in initSensEdges but not here if it
     * filtered out by classpath filter.
     */
    private Set<Edge> reachedSensEdges = new HashSet<>();

    //todo investigate using a true call graph for outflows, instead of these maps.
    //Potentially will improve performance, since callbackToOutflowTable contains a lot of repetition.
    //advantage: efficient navigation both upwards and downwards.
    // Could use MethodOrMethodContext, or downright MethodContext
    // to distinguish between edges in the Soot CG and edges in my outflow CG.
    /**
     * Table where rows represent callbacks and row contents are outflows, as breadth-first trees in the call graph.
     * <p>
     * row = callback, value = outflow of that callback.
     * <p>
     * column = node in the outflow,
     * <p>
     * value = parent node in the outflow for given callback and node. Both node and parent node context-sensitive.
     */
    private Table<MethodOrMethodContext, Edge, Edge> callbackToOutflowTable;

    /**
     * From each Edge in the call graph, the set of sensitives it reaches.
     * <p>
     * todo Do not include sensitives checked by try-catch. this would require storing sensitives in context.
     * <p>
     * toperf this collection is likely a huge memory hog.
     */
    private SetMultimap<Edge, Edge> nodesToReachablePresensMap;

    /**
     * Map from sensitives to sets of callbacks.
     */
    private SetMultimap<Edge, MethodOrMethodContext> presensToCallbacksMap;

    /**
     * Table from Callback-sensitive pairs to a boolean value, indicating whether the path from callback to sensitive is
     * ambiguous.
     */
    private Table<MethodOrMethodContext, Edge, Boolean> ambigousPathsTable = HashBasedTable.create();

    public ContextSensOutflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<Edge> sensEdges,
                                      ClasspathFilter classpathFilter, CallGraphPermDefService cgService) {
        this.dummyMainMethod = dummyMainMethod;
        this.initSensEdges = sensEdges;
        this.classpathFilter = classpathFilter;
        this.cgService = cgService;

        pointsToAnalysis = Scene.v().getPointsToAnalysis();
        if (pointsToAnalysis.getClass() != GeomPointsTo.class) {
            logger.warn("ContextSensOutflowCPHolder is slow with PointsTo algorithms other than GEOM");
        }

        callbackToOutflowTable = buildCallbackToOutflowMap();
        presensToCallbacksMap = buildSensitiveInCToCallbacksMap();
        nodesToReachablePresensMap = buildNodesToReachablePresensMap();
    }

    private Table<MethodOrMethodContext, Edge, Edge> buildCallbackToOutflowMap() {
        uiCallbacksBiMap = StreamUtil.asStream(callGraph.edgesOutOf(dummyMainMethod))
                .collect(Collectors.toMap(
                        Edge::getTgt,
                        edge -> edge,

                        //if there are multiple incoming edges for the same callback, we'll only select the first
                        (edge1, edge2) -> edge1,
                        HashBiMap::create
                ));
        logger.info("\n\nTotal callbacks: " + uiCallbacksBiMap.size() + "\n");

        Table<MethodOrMethodContext, Edge, Edge> table = HashBasedTable.create();
        for (MethodOrMethodContext callback : uiCallbacksBiMap.keySet()) {
            Map<Edge, Edge> outflow = getBreadthFirstOutflow(uiCallbacksBiMap.get(callback));
            if (!Sets.intersection(outflow.keySet(), initSensEdges).isEmpty()) {
                table.row(callback).putAll(outflow);
            }

            long newTime = System.currentTimeMillis();
            logger.info("DP: Callback processed: " + callback + " in " + (newTime - time) / 1E3 + " sec");
            time = newTime;
        }

        return table;
    }

    /**
     * Produces the outflow tree starting from the root method, by breadth-first traversal.
     *
     * @return A map from nodes in the outflow to their parent.
     */
    private Map<Edge, Edge> getBreadthFirstOutflow(Edge callbackEdge) {
        Queue<Edge> queue = new ArrayDeque<>();
        Set<Edge> traversed = new HashSet<>();

        Map<Edge, Edge> outflow = new HashMap<>();
        queue.add(callbackEdge);
        traversed.add(callbackEdge);

        for (Edge crntEdge = queue.poll(); crntEdge != null; crntEdge = queue.poll()) {
            final Edge srcEdge = crntEdge; //to make lambda expressions happy
            MethodOrMethodContext srcMeth = srcEdge.getTgt();
            if (srcMeth.method().hasActiveBody() &&
                    //only analyze the body of methods accepted by classpathFilter
                    classpathFilter.test(srcMeth.method())) {
                srcMeth.method().getActiveBody().getUnits().forEach(
                        (Unit unit) -> getUnitEdgeIterator(unit, srcEdge.srcStmt(), callGraph)
                                .forEachRemaining((Edge tgtEdge) -> {
                                    if (!traversed.contains(tgtEdge)) {
                                        traversed.add(tgtEdge);
                                        queue.add(tgtEdge);
                                        outflow.put(tgtEdge, srcEdge);
                                        if (initSensEdges.contains(tgtEdge)) {
                                            reachedSensEdges.add(tgtEdge);
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
    private Iterator<Edge> getUnitEdgeIterator(Unit unit, Stmt context, CallGraph cg) {
        InstanceInvokeExpr virtualInvoke = PointsToUtil.getVirtualInvokeIfPresent((Stmt) unit);
        Iterator<Edge> edgesIterator = cg.edgesOutOf(unit);

        //Points-to is safe to compute only when there is at least one edge present.
        //Also checking for edges presence first is a performance improvement.
        if (edgesIterator.hasNext() && virtualInvoke != null && context != null) {
            PointsToSet pointsToSet = getPointsToForOutflows(unit, context);
            if (pointsToSet == null || pointsToSet.possibleTypes().isEmpty()) {
                //Computing points-to has thrown an exception or has beed disabled.
                //Also if possibleTypes() is empty, this might be a case of points-to inconsistency with valid edges.
                //Disabling points-to refinement for this unit.
                return edgesIterator;
            }

            SootMethod staticTargetMethod;
            try {
                staticTargetMethod = virtualInvoke.getMethod();
            } catch (Exception e) {
                logger.error(e.getMessage());
                return edgesIterator;
            }
            Set<Type> pointsToTargetTypes = pointsToSet.possibleTypes();
            List<SootMethod> pointsToTargetMethods =
                    HierarchyUtil.resolveHybridDispatch(staticTargetMethod, pointsToTargetTypes);

            //todo: more precise support for fake edges - take into account the changed target.
            //Fake edges alter the natural mapping between edge.srcStmt() => edge.tgt()
            //  e.g. the actually invoked method is a different than the one allowed by class hierarchy.
            //Problems with fake edges:
            // 1. They alter the invoked method. Ex: Thread.start() => Thread.run().
            //      edge.srcStmt()...getTgt() != edge.tgt()
            // 2. They might alter invocation target. Ex: executor.submit(r) => r.run()
            //      edge.srcStmt()...getBase()
            //          != actual receiver inside OFCGB.methodToReceivers.get(edge.srcStmt()...getTgt())
            //      How to get it???
            //      v1: Get it correctly from OnFlyCallGraphBuilder.
            //      v2: Hack it for every particular implementation of fake edge.

            //Why context sensitivity works for Thread.start()?
            //  Current algorithm won't distinguish between 2 statements Thread.start() within the same method,
            //  but it doesn't matter for the purpose of DroidPerm.

            List<Edge> edges = Lists.newArrayList(edgesIterator);
            List<Edge> fakeEdges = edges.stream().filter(edge -> edge.kind().isFake()).collect(Collectors.toList());

            //if there are fake edges, filter out all other edges
            //Exception: for AsyncTask crafted CP edges are better than fake edges.
            if (!fakeEdges.isEmpty() && fakeEdges.stream().noneMatch(fake -> fake.kind().isAsyncTask())) {
                return fakeEdges.iterator();
            } else {
                return edges.stream()
                        .filter(edge -> edgeMatchesPointsTo(edge, pointsToTargetMethods))
                        .iterator();
            }
        }

        //default case, anything except virtual method calls
        return edgesIterator;
    }

    private boolean edgeMatchesPointsTo(Edge edge, List<SootMethod> pointsToTargetMethods) {
        //     This is the main case: real edges
        return pointsToTargetMethods.contains(edge.getTgt().method())
                //2nd case: fake edges
                //Fake edges are a hack in Soot for handling async constructs.
                //If it's a fake edge, include it without comparing to actual targets.
                //toperf no longer necessary, because if there are fake edges this method won't be called
                || (edge.kind().isFake());
                /* Fake edges could essentially be replaced with crafted JDK classes, with one exception:
                ExecutorService.execute(), or edges of kind EXECUTOR.

                Reason: cannot disable fake edges kind EXECUTOR, because it would require crafting
                a custom executor.execute() for every implementation of ExecutorService. Those fake edges are still
                needed when executor.execute() is called directly by the app.

                 The only drawback is a bit uglier paths, due to fake edge being logged instead of a nice crafted one.
                */
    }

    private SetMultimap<Edge, MethodOrMethodContext> buildSensitiveInCToCallbacksMap() {
        return reachedSensEdges.stream().collect(MyCollectors.toMultimap(
                sensitiveInContext -> sensitiveInContext,
                sensitiveInContext -> callbackToOutflowTable.rowMap().entrySet().stream()
                        .filter(cbToOutflowEntry -> cbToOutflowEntry.getValue().containsKey(sensitiveInContext))
                        .map(Map.Entry::getKey)
        ));
    }

    private SetMultimap<Edge, Edge> buildNodesToReachablePresensMap() {
        SetMultimap<Edge, Edge> result = HashMultimap.create();
        for (MethodOrMethodContext callback : callbackToOutflowTable.rowKeySet()) {
            for (Edge presensEdge : reachedSensEdges) {
                if (callbackToOutflowTable.contains(callback, presensEdge)) {
                    Edge edge = presensEdge;
                    //populate the result map across the path from callback to presensEdge
                    while (edge != null && edge.getTgt() != callback) {
                        result.put(edge, presensEdge);
                        edge = callbackToOutflowTable.row(callback).get(edge);
                    }
                    if (edge != null) {
                        result.put(edge, presensEdge);
                    }
                }
            }
        }
        return result;
    }

    public void printPathsFromCallbackToSensitive() {
        System.out.println("\nPaths from each callback to each sensitive");
        System.out.println("========================================================================\n");

        for (MethodOrMethodContext callback : callbackToOutflowTable.rowKeySet()) {
            for (Edge sensitiveInContext : reachedSensEdges) {
                if (callbackToOutflowTable.row(callback).containsKey(sensitiveInContext)) {
                    printPath(callback, sensitiveInContext, callbackToOutflowTable.row(callback));
                }
            }
        }
    }

    private void printPath(MethodOrMethodContext callback, Edge destEdge, Map<Edge, Edge> outflow) {
        List<Edge> path = computePathFromOutflow(callback, destEdge, outflow);
        boolean ambiguous = false;

        System.out.println("From " + callback);
        cgService.printSensitiveHeader(destEdge.getTgt(), "  ");
        cgService.printSensitiveContext(destEdge, "\t\t");
        System.out.println("----------------------------------------------------------------------------------------");
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                Edge edge = path.get(i);
                Edge childEdge = i < path.size() - 1 ? path.get(i + 1) : null;
                if (edge != null) {
                    Pair<String, Boolean> edgeData = pathNodeToString(edge, childEdge);
                    System.out.println(edgeData.getO1());
                    ambiguous |= edgeData.getO2();
                } else {
                    System.out.println((Object) null);
                }
            }
            if (!ambiguous) {
                System.out.println("\tPATH NON-AMBIGUOUS");
            }
            ambigousPathsTable.put(callback, destEdge, ambiguous);
            System.out.println();
        } else {
            System.out.println("Not found!");
        }
        System.out.println();
    }

    /**
     * @param edge      currently printed method call
     * @param childEdge childEdge of edge
     * @return A pair of: 1. string to print representing edge. 2. Whether path is points-to ambiguous.
     */
    private Pair<String, Boolean> pathNodeToString(Edge edge, Edge childEdge) {
        StringBuilder out = new StringBuilder();
        boolean ambiguous = false;

        //parent method
        out.append(edge.getTgt());

        //invocation line number in parent method
        if (childEdge != null && childEdge.srcStmt() != null) {
            out.append(" : ").append(childEdge.srcStmt().getJavaSourceStartLineNumber());
        }

        //points to of the invocation target
        boolean printPointsTo =
                childEdge != null
                        && PointsToUtil.getVirtualInvokeIfPresent(childEdge.srcStmt()) != null;
        if (printPointsTo) {
            PointsToSet pointsTo = getPointsToForLogging(childEdge.srcStmt(), edge.srcStmt());
            out.append("\n                                                                p-to: ");
            if (pointsTo != null) {
                out.append(pointsTo.possibleTypes().stream()
                        .map(type -> type.toString().substring(type.toString().lastIndexOf(".") + 1))
                        .collect(Collectors.toList()));
            } else {
                out.append("exception");
            }
            int edgesCount = Iterators.size(callGraph.edgesOutOf(childEdge.srcStmt()));
            out.append(", edges: ").append(edgesCount);
            ambiguous = edgesCount >= 2 &&
                    (pointsTo == null || pointsTo.possibleTypes().isEmpty() || pointsTo.possibleTypes().size() >= 2);
        }

        //shortcutted call, if it's a fake edge
        if (childEdge != null && childEdge.kind().isFake()) { //childEdge edge is always != null
            SootMethod shortcuttedMethod;
            try {
                shortcuttedMethod = childEdge.srcStmt().getInvokeExpr().getMethod();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                shortcuttedMethod = null;
            }
            out.append("\n    ");
            out.append(shortcuttedMethod);
            out.append(
                    "\n                                                                FAKE edge: call shortcutted");
        }

        return new Pair<>(out.toString(), ambiguous);
    }

    private List<Edge> computePathFromOutflow(MethodOrMethodContext src, Edge dest,
                                              Map<Edge, Edge> outflow) {
        List<Edge> path = new ArrayList<>();
        Edge edge = dest;
        while (edge != null && edge.getTgt() != src) {
            path.add(edge);
            edge = outflow.get(edge);
        }
        path.add(edge != null ? edge : null);
        Collections.reverse(path);
        return path;
    }

    protected PointsToSet getPointsToForOutflows(Unit unit, Stmt context) {
        return PointsToUtil.getPointsToIfVirtualCall(unit, context, pointsToAnalysis);
    }

    protected PointsToSet getPointsToForLogging(Stmt stmt, Stmt context) {
        return PointsToUtil.getPointsToIfVirtualCall(stmt, context, pointsToAnalysis);
    }

    public Set<Edge> getReacheablePresensitives(Edge edge) {
        return nodesToReachablePresensMap.get(edge);
    }

    public Set<Edge> getCallsToSensitiveFor(MethodOrMethodContext callback) {
        return Sets.intersection(callbackToOutflowTable.row(callback).keySet(), reachedSensEdges);
    }

    /**
     * To be used for checkers only.
     * <p>
     * LIMITATION: This method does not check points-to consistency between parent edges and child
     * edges.
     * <p>
     * For methods executed directly inside callback, parent will be the edge from dummy main to callback.
     */
    public Set<Edge> getParentEdges(Edge edge, MethodOrMethodContext callback) {
        if (!callbackToOutflowTable.contains(callback, edge)) {
            return Collections.emptySet();
        }

        Iterator<Edge> allParentEdges = callGraph.edgesInto(edge.getSrc());
        return StreamUtil.asStream(allParentEdges)
                .filter(parentEdge -> (callbackToOutflowTable.contains(callback, parentEdge)
                        || edge.getSrc() == callback))
                .collect(Collectors.toSet());
    }

    public Set<MethodOrMethodContext> getUiCallbacks() {
        return uiCallbacksBiMap.keySet();
    }

    /**
     * Returns only the reachable callbacks, e.g. those that reach sensitives.
     * <p>
     * We also sort the callbacks by their class name followed by method declaration line number.
     */
    public Set<MethodOrMethodContext> getSortedReachableCallbacks() {
        return callbackToOutflowTable.rowKeySet().stream().sorted(SortUtil.methodOrMCComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<MethodOrMethodContext> getReachingCallbacks(Edge edge) {
        return presensToCallbacksMap.get(edge);
    }

    /**
     * If parent edge doesn't come directly from dummy main, we'll get callbacks for the parent. Otherwise - callbacks
     * for the child.
     * <p>
     * Limitation: This implementation doesn't account for parent-child points-to consistency. Only for checkers.
     */
    Set<MethodOrMethodContext> getReachingCallbacks(Pair<Edge, Edge> edgeParentPair) {
        Edge edge = edgeParentPair.getO1();
        Edge parent = edgeParentPair.getO2();
        Edge target = (parent != null && parent.getSrc() != dummyMainMethod) ? parent : edge;
        return callbackToOutflowTable.column(target).keySet();
    }

    public boolean isPathAmbiguous(MethodOrMethodContext callback, Edge sensInContext) {
        return ambigousPathsTable.get(callback, sensInContext);
    }

    public Set<Edge> getReachedSensEdges() {
        return reachedSensEdges;
    }
}

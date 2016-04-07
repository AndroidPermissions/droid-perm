package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.CachingSupplier;
import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 3/28/2016.
 */
public class ContextSensOutflowCPHolder implements CallPathHolder {

    private MethodOrMethodContext dummyMainMethod;
    private Set<MethodOrMethodContext> consumers;
    private Set<MethodInContext> consumersInContext = new HashSet<>();

    /**
     * Map from UI callbacks to their outflows, as breadth-first trees in the call graph.
     * <br/>
     * 1-st level map: key = callback, value = outflow of that callback.
     * <br/>
     * 2-nd level map: key = node in the outflow, value = parent node. Both are context-sensitive.
     */
    private Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> callbackToOutflowMap;

    /**
     * Map from consumers to sets of callbacks.
     */
    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> consumerCallbacks;

    public ContextSensOutflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> consumers) {
        this.dummyMainMethod = dummyMainMethod;
        this.consumers = consumers;
        callbackToOutflowMap = buildCallbackToOutflowMap();
        consumerCallbacks = buildConsumerCallbacksFromOutflows();
    }

    private Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> buildCallbackToOutflowMap() {
        Map<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> map = new HashMap<>();
        for (MethodOrMethodContext callback : getUICallbacks()) {
            Map<MethodInContext, MethodInContext> outflow = getBreadthFirstOutflow(callback);

            Collection<MethodOrMethodContext> outflowNodes =
                    outflow.keySet().stream().map(pair -> pair.method).collect(Collectors.toList());
            if (!Collections.disjoint(outflowNodes, consumers)) {
                map.put(callback, outflow);
            }
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
     * @return A map from nodes in the outflow to the edge leading to its parent. Elements are of the form
     * (N, Edge(src = P, dest = N))
     */
    //todo won't work if 2 runnables are touched from the same callback
    //I have to put true context-sensitive MethodContext for thread.run() methods.
    //todo also, speed is extremely slow on conversations
    private Map<MethodInContext, MethodInContext> getBreadthFirstOutflow(MethodOrMethodContext root) {
        CallGraph cg = Scene.v().getCallGraph();
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
        Queue<MethodInContext> queue = new ArrayDeque<>();
        Set<MethodInContext> traversed = new HashSet<>();

        //a map from child to parent edge is equivalent to 1-CFA cotnext sensitivity.
        Map<MethodInContext, MethodInContext> outflow = new HashMap<>();
        MethodInContext rootInContext = new MethodInContext(root, null);
        queue.add(rootInContext);
        traversed.add(rootInContext);


        for (MethodInContext meth = queue.poll(); meth != null; meth = queue.poll()) {
            final MethodInContext methCopy = meth; //to make lambda expressions happy
            MethodOrMethodContext srcMeth = meth.method;
            Context context = meth.context;
            if (srcMeth.method().hasActiveBody()) {
                srcMeth.method().getActiveBody().getUnits().stream().forEach(
                        (Unit unit) -> getUnitEdgeIterator(unit, context, cg, pta).forEachRemaining((Edge edge) -> {
                            MethodInContext tgtInContext = MethodInContext.forTarget(edge);

                            if (!traversed.contains(tgtInContext)) {
                                traversed.add(tgtInContext);
                                queue.add(tgtInContext);
                                outflow.put(tgtInContext, methCopy);
                                if (consumers.contains(edge.getTgt())) {
                                    consumersInContext.add(tgtInContext);
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
    private static Iterator<Edge> getUnitEdgeIterator(Unit unit, Context context, CallGraph cg,
                                                      PointsToAnalysis pta) {
        if (unit instanceof InvokeStmt && context != null) {
            InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
            // only virtual invocations require context sensitivity.
            if (invokeExpr instanceof VirtualInvokeExpr || invokeExpr instanceof InterfaceInvokeExpr) {
                InstanceInvokeExpr invoke = (InstanceInvokeExpr) invokeExpr;
                SootMethod staticTargetMethod = invoke.getMethod();
                //in Jimple target is always Local, regardless of who is the qualifier in Java.
                Local target = (Local) invoke.getBase();

                //we canot compute this list if there are no edges, hence the need for a supplier
                Supplier<List<SootMethod>> actualTargetMethods = new CachingSupplier<>(() -> {
                    Set<Type> targetPossibleTypes = pta.reachingObjects(context, target).possibleTypes();
                    return HierarchyUtil.resolveHybridDispatch(staticTargetMethod, targetPossibleTypes);
                });

                return StreamUtil.asStream(cg.edgesOutOf(unit))
                        //Hack required due to hacks of class Thread in Soot:
                        //if it's a THREAD edge, include it without comparing to actual targets
                        .filter(edge -> edge.kind() == Kind.THREAD
                                || actualTargetMethods.get().contains(edge.getTgt().method()))
                        .iterator();
            }
        }

        //default case, anything except virtual method calls
        return cg.edgesOutOf(unit);
    }

    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> buildConsumerCallbacksFromOutflows() {
        return consumersInContext.stream().collect(Collectors.toMap(
                consumerInContext -> consumerInContext.method,
                consumerInContext -> callbackToOutflowMap.entrySet().stream()
                        .filter(entry -> entry.getValue().containsKey(consumerInContext)).map(Map.Entry::getKey).
                                collect(Collectors.toSet()),
                //merge function required, because 2 consumerInContext could map to the same consumer
                (set1, set2) -> { //merge function, concatenating 2 sets of callbacks
                    set1.addAll(set2);
                    return set1;
                }
        ));
    }

    @Override
    public void printPathsFromCallbackToConsumer() {
        System.out.println("\nPaths from each callback to each consumer");
        System.out.println("============================================\n");


        for (Map.Entry<MethodOrMethodContext, Map<MethodInContext, MethodInContext>> entry : callbackToOutflowMap
                .entrySet()) {
            for (MethodInContext consumerInContext : consumersInContext) {
                if (entry.getValue().containsKey(consumerInContext)) {
                    printPath(entry.getKey(), consumerInContext, entry.getValue());
                }
            }
        }
    }

    private void printPath(MethodOrMethodContext src, MethodInContext dest,
                           Map<MethodInContext, MethodInContext> outflow) {
        List<MethodOrMethodContext> path = computePathFromOutflow(src, dest, outflow);

        System.out.println("From " + src + "\n  to " + dest);
        System.out.println("--------------------------------------------");
        if (path != null) {
            path.forEach(System.out::println);
        } else {
            System.out.println("Not found!");
        }
        System.out.println();
    }

    private List<MethodOrMethodContext> computePathFromOutflow(MethodOrMethodContext src, MethodInContext dest,
                                                               Map<MethodInContext, MethodInContext> outflow) {
        List<MethodOrMethodContext> path = new ArrayList<>();
        MethodInContext node = dest;
        while (node != null && node.method != src) {
            path.add(node.method);
            node = outflow.get(node);
        }
        path.add(node != null ? node.method : null);
        Collections.reverse(path);
        return path;
    }

    @Override
    public Set<MethodOrMethodContext> getCallbacks(MethodOrMethodContext consumer) {
        return consumerCallbacks.get(consumer);
    }

}

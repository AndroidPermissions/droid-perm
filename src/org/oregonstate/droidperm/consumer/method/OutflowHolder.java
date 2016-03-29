package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.StreamUtil;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 3/28/2016.
 */
public class OutflowHolder {

    //todo any of the methods can be null if no Thread is encountered in the call graph.
    //For some soot-related reason expression Thread.start() actually invokes Thread.run().
    final static SootClass threadClazz = Scene.v().getSootClassUnsafe("java.lang.Thread");
    final static SootMethod threadStart = threadClazz.getMethodUnsafe("void start()");
    final static SootMethod threadRunMeth = threadClazz.getMethodUnsafe("void run()");
    final static SootMethod threadCons = threadClazz.getMethodUnsafe("void <init>(java.lang.Runnable)");

    private MethodOrMethodContext dummyMainMethod;
    private Set<MethodOrMethodContext> consumers;

    /**
     * Map from UI callbacks to their outflows, as breadth-first trees in the call graph.
     * <br/>
     * 1-st level map: key = callback, value = outflow of that callback.
     * <br/>
     * 2-nd level map: key = node in the outflow, value = edge to its parent. Entries are of the form:
     * (N, Edge(src = P, dest = N))
     */
    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> callbackToOutflowMap;

    /**
     * Map from consumers to sets of callbacks.
     */
    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> consumerCallbacks;

    public OutflowHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> consumers) {
        this.dummyMainMethod = dummyMainMethod;
        this.consumers = consumers;
        callbackToOutflowMap = buildCallbackToOutflowMap();
        consumerCallbacks = buildConsumerCallbacksFromOutflows();
    }

    public Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getConsumerCallbacks() {
        return consumerCallbacks;
    }

    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> buildCallbackToOutflowMap() {
        Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> map = new HashMap<>();
        for (MethodOrMethodContext callback : getUICallbacks()) {
            Map<MethodOrMethodContext, Edge> outflow = getBreadthFirstOutflow(callback);

            if (!Collections.disjoint(outflow.keySet(), consumers)) {
                map.put(callback, outflow);
            }
        }
        return map;
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
    private static Map<MethodOrMethodContext, Edge> getBreadthFirstOutflow(MethodOrMethodContext root) {
        CallGraph cg = Scene.v().getCallGraph();
        Queue<MethodOrMethodContext> queue = new ArrayDeque<>();
        Set<MethodOrMethodContext> traversed = new HashSet<>();
        Map<MethodOrMethodContext, Edge> outflow = new HashMap<>();
        queue.add(root);
        traversed.add(root);

        for (MethodOrMethodContext node = queue.poll(); node != null; node = queue.poll()) {
            Iterator<EdgeWrap> it = new EdgeIterator(node, cg);
            while (it.hasNext()) {
                EdgeWrap edgeWrap = it.next();
                MethodOrMethodContext tgt = edgeWrap.edge.getTgt();

                if (!traversed.contains(tgt)) {
                    traversed.add(tgt);
                    outflow.put(tgt, edgeWrap.edge);

                    if (edgeWrap.follow) {
                        queue.add(tgt);
                    }
                }
            }
        }
        return outflow;
    }

    private Collection<MethodOrMethodContext> getUICallbacks() {
        List<MethodOrMethodContext> list = new ArrayList<>();
        Scene.v().getCallGraph().edgesOutOf(dummyMainMethod).forEachRemaining(edge -> list.add(edge.getTgt()));
        return list;
    }

    public void printPathsFromCallbackToConsumerThroughOutflows() {
        System.out.println("\nPaths from each callback to each consumer");
        System.out.println("============================================\n");


        for (Map.Entry<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> entry : callbackToOutflowMap
                .entrySet()) {
            for (MethodOrMethodContext consumer : consumers) {
                if (entry.getValue().containsKey(consumer)) {
                    printPath(entry.getKey(), consumer, entry.getValue());
                }
            }
        }
    }

    private void printPath(MethodOrMethodContext src, MethodOrMethodContext dest,
                           Map<MethodOrMethodContext, Edge> outflow) {
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

    private List<MethodOrMethodContext> computePathFromOutflow(MethodOrMethodContext src, MethodOrMethodContext dest,
                                                               Map<MethodOrMethodContext, Edge> outflow) {
        List<MethodOrMethodContext> path = new ArrayList<>();
        MethodOrMethodContext node = dest;
        while (node != src) {
            path.add(node);
            node = outflow.get(node).getSrc();
        }
        path.add(node);
        Collections.reverse(path);
        return path;
    }

    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> buildConsumerCallbacksFromOutflows() {
        return consumers.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> callbackToOutflowMap.entrySet().stream()
                        .filter(entry -> entry.getValue().containsKey(consumer)).map(Map.Entry::getKey).
                                collect(Collectors.toSet())
        ));
    }

    /**
     * Iterator over outbound edges of a particular method.
     */
    private static class EdgeIterator implements Iterator<EdgeWrap> {
        private final MethodOrMethodContext node;
        private final CallGraph cg;
        Iterator<Unit> units;
        Iterator<EdgeWrap> unitEdgeIterator;
        EdgeWrap currentEdge;

        /* Thread-invocation context:
        From Thread local var name to its runtime type*/
        Map<String, Type> threadRunnableTypes = new HashMap<>();

        public EdgeIterator(MethodOrMethodContext node, CallGraph cg) {
            this.node = node;
            this.cg = cg;
            units = node.method().hasActiveBody() ? node.method().getActiveBody().getUnits().iterator() :
                    Collections.emptyIterator();
            unitEdgeIterator = null;
            currentEdge = nextImpl();
        }

        @Override
        public boolean hasNext() {
            return currentEdge != null;
        }

        @Override
        public EdgeWrap next() {
            if (currentEdge == null) {
                throw new NoSuchElementException();
            }

            EdgeWrap result = currentEdge;
            currentEdge = nextImpl();
            return result;
        }

        private EdgeWrap nextImpl() {
            if (unitEdgeIterator != null && unitEdgeIterator.hasNext()) {
                return unitEdgeIterator.next();
            } else {
                while (units.hasNext()) {
                    Unit currentUnit = units.next();
                    unitEdgeIterator = getUnitEdgeIterator(currentUnit);
                    if (unitEdgeIterator.hasNext()) {
                        return unitEdgeIterator.next();
                    }
                }
            }
            return null;
        }

        private Iterator<EdgeWrap> getUnitEdgeIterator(Unit unit) {
            if (unit instanceof InvokeStmt && ((InvokeStmt) unit).getInvokeExpr().getMethod().equals(threadCons)) {
                InstanceInvokeExpr invokeExpr = ((InstanceInvokeExpr) ((InvokeStmt) unit).getInvokeExpr());

                threadRunnableTypes.put(invokeExpr.getBase().toString(), invokeExpr.getArg(0).getType());
            }

            threadStartCase:
            if (unit instanceof InvokeStmt && ((InvokeStmt) unit).getInvokeExpr().getMethod().equals(threadStart)) {
                //special threatment for thread.start
                InstanceInvokeExpr invokeExpr = ((InstanceInvokeExpr) ((InvokeStmt) unit).getInvokeExpr());
                RefType runnableType = (RefType) threadRunnableTypes.get(invokeExpr.getBase().toString());
                if (runnableType == null) {
                    //no tag for this expression found, default Runnable treatment.
                    break threadStartCase;
                }

                Optional<Edge> optional = StreamUtil.asStream(cg.edgesOutOf(unit))
                        .filter(edge -> edge.getTgt().method() == threadRunMeth).findFirst();

                if (optional.isPresent()) {
                    Edge threadRunEdge = optional.get();
                    MethodOrMethodContext threadRun = threadRunEdge.getTgt();
                    Optional<Edge> runnableRunEdgeOpt = StreamUtil.asStream(cg.edgesOutOf(threadRun))
                            .filter(edge -> edge.getTgt().method().getDeclaringClass() == runnableType.getSootClass())
                            .findFirst();
                    if (runnableRunEdgeOpt.isPresent()) {
                        System.out.println("Selected edge out of Thread.run(): " + runnableRunEdgeOpt.get());

                        //Return the edge from from thread to Runnable.run() only.
                        // We skip the edge from current unit to Thread.run(),
                        // because it will lead to analyzing the inside of Thread.run() in a regular way,
                        // cancelling any context-sensitivity.
                        //The problem with missing Thread.run() is purely cosmetical in the logged paths.
                        return Arrays.asList(
                                new EdgeWrap(threadRunEdge, false),
                                new EdgeWrap(runnableRunEdgeOpt.get(), true)).iterator();
                    }
                }
            }

            //default case, all calls except a tagged Thread.start()
            return StreamUtil.asStream(cg.edgesOutOf(unit)).map(edge -> new EdgeWrap(edge, true)).iterator();
        }
    }

    //toopt should be a memory hog, if I stick to ad-hoc implementation, EdgeWrap should be eliminated.
    private static class EdgeWrap {

        public EdgeWrap(Edge edge, boolean follow) {
            this.edge = edge;
            this.follow = follow;
        }

        Edge edge;
        boolean follow;
    }
}

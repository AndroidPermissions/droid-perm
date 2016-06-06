package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.StreamUtil;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 3/28/2016.
 */
public class OutflowCPHolder extends AbstractCallPathHolder {

    /**
     * Map from UI callbacks to their outflows, as breadth-first trees in the call graph.
     * <p/>
     * 1-st level map: key = callback, value = outflow of that callback.
     * <p/>
     * 2-nd level map: key = node in the outflow, value = edge to its parent. Entries are of the form: (N, Edge(src = P,
     * dest = N))
     */
    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> callbackToOutflowMap;

    /**
     * Map from sensitives to sets of callbacks.
     */
    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> consumerCallbacks;

    public OutflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> consumers) {
        super(dummyMainMethod, consumers);
        callbackToOutflowMap = buildCallbackToOutflowMap();
        consumerCallbacks = buildConsumerCallbacksFromOutflows();
    }

    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> buildCallbackToOutflowMap() {
        Map<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> map = new HashMap<>();
        for (MethodOrMethodContext callback : getUICallbacks()) {
            Map<MethodOrMethodContext, Edge> outflow = getBreadthFirstOutflow(callback);

            if (!Collections.disjoint(outflow.keySet(), sensitives)) {
                map.put(callback, outflow);
            }
        }
        return map;
    }

    /**
     * Produces the outflow tree starting from the root method, by breadth-first traversal.
     *
     * @return A map from nodes in the outflow to the edge leading to its parent.
     * <p>
     * Elements are of the form (N, Edge(src = P, dest = N))
     */
    private static Map<MethodOrMethodContext, Edge> getBreadthFirstOutflow(MethodOrMethodContext root) {
        CallGraph cg = Scene.v().getCallGraph();
        Queue<MethodOrMethodContext> queue = new ArrayDeque<>();
        Set<MethodOrMethodContext> traversed = new HashSet<>();
        Map<MethodOrMethodContext, Edge> outflow = new HashMap<>();
        queue.add(root);
        traversed.add(root);

        for (MethodOrMethodContext node = queue.poll(); node != null; node = queue.poll()) {
            //DebugUtil.debugEdgesOutOf(cg, node);
            Iterator<Edge> it = cg.edgesOutOf(node);
            while (it.hasNext()) {
                Edge edge = it.next();
                MethodOrMethodContext tgt = edge.getTgt();

                if (!traversed.contains(tgt)) {
                    traversed.add(tgt);
                    queue.add(tgt);
                    outflow.put(tgt, edge);
                }
            }
        }
        return outflow;
    }

    private Set<MethodOrMethodContext> getUICallbacks() {
        return StreamUtil.asStream(Scene.v().getCallGraph().edgesOutOf(dummyMainMethod))
                .map(Edge::getTgt).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> buildConsumerCallbacksFromOutflows() {
        return sensitives.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> callbackToOutflowMap.entrySet().stream()
                        .filter(entry -> entry.getValue().containsKey(consumer)).map(Map.Entry::getKey).
                                collect(Collectors.toSet())
        ));
    }

    @Override
    public void printPathsFromCallbackToSensitive() {
        System.out.println("\nPaths from each callback to each consumer");
        System.out.println("============================================\n");


        for (Map.Entry<MethodOrMethodContext, Map<MethodOrMethodContext, Edge>> entry : callbackToOutflowMap
                .entrySet()) {
            for (MethodOrMethodContext consumer : sensitives) {
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

    @Override
    public Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap() {
        return consumerCallbacks;
    }
}

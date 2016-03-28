package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class CallGraphUtil {

    /**
     * Return the subset of methods contained in the call graph, out of the input collection.
     *
     * @param methods - input collection
     */
    public static Set<MethodOrMethodContext> getContainedMethods(Collection<SootMethod> methods) {
        CallGraph callGraph = Scene.v().getCallGraph();
        return methods.stream()
                // only keep methods that have an edge into, e.g. are in the call graph
                .filter(meth -> callGraph.edgesInto(meth).hasNext())
                .collect(Collectors.toSet());
    }

    public static List<MethodOrMethodContext> getInflow(Set<MethodOrMethodContext> methods) {
        return new TransitiveSources(Scene.v().getCallGraph()).getInflow(methods);
    }

    public static List<Edge> getInflowCallGraph(MethodOrMethodContext method) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(Collections.singletonList(method));
    }

    public static List<Edge> getInflowCallGraph(Set<MethodOrMethodContext> methods) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(methods);
    }

    /**
     * Produces the outflow tree starting from the root method, by breadth-first traversal.
     *
     * @return A map from nodes in the outflow to the edge leading to its parent. Elements are of the form
     * (N, Edge(src = P, dest = N))
     */
    public static Map<MethodOrMethodContext, Edge> getBreadthFirstOutflow(MethodOrMethodContext root) {
        CallGraph cg = Scene.v().getCallGraph();
        Queue<MethodOrMethodContext> queue = new ArrayDeque<>();
        Set<MethodOrMethodContext> traversed = new HashSet<>();
        Map<MethodOrMethodContext, Edge> outflow = new HashMap<>();
        queue.add(root);
        traversed.add(root);

        for (MethodOrMethodContext node = queue.poll(); node != null; node = queue.poll()) {
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
}

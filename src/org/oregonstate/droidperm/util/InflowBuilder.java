package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class InflowBuilder {
    private CallGraph parentGraph;

    public InflowBuilder(CallGraph parentGraph) {
        this.parentGraph = parentGraph;
    }

    /**
     * @return the inflow graph (as collection of edges) for the given starting methods
     */
    public List<Edge> getInflow(Collection<MethodOrMethodContext> methods) {
        // Probably could be merged into next method, unless next one is valuable separately
        //concatenate streams of "edges into" for each method in the input
        List<Edge> edges = methods.stream()
                .flatMap(meth -> StreamUtil.asStream(parentGraph.edgesInto(meth)))
                .collect(Collectors.toList());
        return getInflowForEdges(edges);
    }

    /**
     * @return the inflow graph (as collection of edges) for the given starting edges
     */
    public List<Edge> getInflowForEdges(Collection<Edge> edges) {
        Set<Edge> set = new HashSet<>();
        List<Edge> worklist = edges.stream()
                .filter(set::add) //stateful lambda, ensures unicity
                .collect(Collectors.toList());

        for (int i = 0; i < worklist.size(); i++) {
            Edge edge = worklist.get(i);
            Iterator<Edge> it = parentGraph.edgesInto(edge.getSrc());
            while (it.hasNext()) {
                Edge newEdge = it.next();
                if (set.add(newEdge)) worklist.add(newEdge);
            }
        }

        return worklist;
    }
}

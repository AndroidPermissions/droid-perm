package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class InflowBuilder {
    private CallGraph parentGraph;

    public InflowBuilder(CallGraph parentGraph) {
        this.parentGraph = parentGraph;
    }

    public List<Edge> getInflow(Iterator<MethodOrMethodContext> methods) {
        ArrayList<Edge> edges = new ArrayList<>();
        while (methods.hasNext()) {
            Iterator<Edge> it = parentGraph.edgesInto(methods.next());
            while (it.hasNext()) {
                edges.add(it.next());
            }
        }
        return getInflowForEdges(edges.iterator());
    }

    public List<Edge> getInflowForEdges(Iterator<Edge> edges) {
        Set<Edge> s = new HashSet<>();
        ArrayList<Edge> worklist = new ArrayList<>();
        while (edges.hasNext()) {
            Edge edge = edges.next();
            if (s.add(edge)) {
                worklist.add(edge);
            }
        }

        for (int i = 0; i < worklist.size(); i++) {
            Edge edge = worklist.get(i);
            Iterator<Edge> it = parentGraph.edgesInto(edge.getSrc());
            while (it.hasNext()) {
                Edge e = it.next();
                if (s.add(e)) worklist.add(e);
            }
        }

        return worklist;
    }
}

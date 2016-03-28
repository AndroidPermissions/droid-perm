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

}

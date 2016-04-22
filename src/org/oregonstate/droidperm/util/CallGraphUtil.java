package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/15/2016.
 */
public class CallGraphUtil {

    public static <T extends SootMethodAndClass> Map<T, Set<MethodOrMethodContext>> resolveCallGraphEntriesToMap(
            Collection<T> methodDefs) {
        Map<T, Set<MethodOrMethodContext>> result = new HashMap<>();
        for (T methodDef : methodDefs) {
            Set<MethodOrMethodContext> methods = getNodesFor(HierarchyUtil.resolveAbstractDispatch(methodDef));

            if (!methods.isEmpty()) {
                result.put(methodDef, methods);
            }
        }
        return result;
    }

    /**
     * Return the subset of methods (eventually in their context) contained in the call graph, out of the input
     * collection.
     *
     * @param methods - input collection
     */
    public static Set<MethodOrMethodContext> getNodesFor(Collection<SootMethod> methods) {
        return methods.stream().map(CallGraphUtil::getNodesFor)
                .collect(MyCollectors.toFlatSet()); //Collect MoMC sets for every method into one set
    }

    public static Set<MethodOrMethodContext> getNodesFor(SootMethod method) {
        CallGraph callGraph = Scene.v().getCallGraph();
        if (callGraph instanceof ContextAwareCallGraph) {
            ContextAwareCallGraph caCallGraph = (ContextAwareCallGraph) callGraph;
            return caCallGraph.getNodes(method);
        } else {
            return callGraph.edgesInto(method).hasNext() || callGraph.edgesOutOf(method).hasNext()
                    ? Collections.singleton(method) : Collections.emptySet();
        }
    }

    public static MethodOrMethodContext getEntryPointMethod(SootMethod method) {
        Set<MethodOrMethodContext> allMatches = getNodesFor(method);
        if (allMatches.isEmpty()) {
            throw new RuntimeException("Entry point method not found: " + method);
        } else if (allMatches.size() > 1) {
            throw new RuntimeException("More than one call graph node found for: " + method);
        } else {
            return allMatches.iterator().next();
        }
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

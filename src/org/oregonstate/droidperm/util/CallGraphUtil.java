package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import java.util.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class CallGraphUtil {

    /**
     * @return an getInflow over all methods in the graph that have the given signature.
     */
    public static Set<MethodOrMethodContext> iterateMethods(CallGraph cg, Collection<SootMethodAndClass> methodDefs) {
        return toSet(new Targets(new Filter(new MultiTargetPredicate(methodDefs)).wrap(cg.iterator())));
    }

    public static <T> Set<T> toSet(Iterator<T> iterator) {
        Set<T> copy = new HashSet<>();
        while (iterator.hasNext())
            copy.add(iterator.next());
        return copy;
    }

    /**
     * Get actual methods in the call graph corresponding to the given definitions.
     */
    public static Set<MethodOrMethodContext> getActualMethods(Collection<SootMethodAndClass> methodDefs) {
        return iterateMethods(Scene.v().getCallGraph(), methodDefs);
    }

    public static List<MethodOrMethodContext> getInflow(Set<MethodOrMethodContext> methods) {
        return new TransitiveSources(Scene.v().getCallGraph()).getInflow(methods.iterator());
    }

    public static List<Edge> getInflowCallGraph(MethodOrMethodContext method) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(Collections.singletonList(method).iterator());
    }

    public static List<Edge> getInflowCallGraph(Set<MethodOrMethodContext> methods) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(methods.iterator());
    }
}

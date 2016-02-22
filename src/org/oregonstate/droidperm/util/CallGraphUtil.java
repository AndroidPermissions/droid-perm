package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class CallGraphUtil {

    /**
     * Get actual methods in the given call graph corresponding to the given definitions.
     */
    public static Set<MethodOrMethodContext> getActualMethods(CallGraph cg, Collection<SootMethodAndClass> methodDefs) {
        return StreamUtils.asStream(cg)
                .filter(new MultiTargetPredicate(methodDefs)).map(Edge::getTgt).collect(Collectors.toSet());
    }

    /**
     * Get actual methods in the default call graph corresponding to the given definitions.
     */
    public static Set<MethodOrMethodContext> getActualMethods(Collection<SootMethodAndClass> methodDefs) {
        return getActualMethods(Scene.v().getCallGraph(), methodDefs);
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

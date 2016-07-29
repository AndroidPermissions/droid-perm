package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/3/2016.
 */
public interface CallPathHolder {

    /**
     * We also sort the callbacks by their class name followed by method declaration line number.
     */
    List<MethodOrMethodContext> getReachableCallbacks();

    Set<MethodOrMethodContext> getReacheableSensitives(Edge edge);

    Set<Edge> getCallsToSensitiveFor(MethodOrMethodContext callback);

    /**
     * For 1-CFA analysis. Map from call to its parent calls, for each call to sensitive in this calblack.
     */
    Map<Edge, Set<Edge>> getContextSensCallsToSensitiveFor(MethodOrMethodContext callback);

    void printPathsFromCallbackToSensitive();

    /**
     * There might be multiple calls to meth in one callback, that's why list is needed.
     */
    List<Edge> getCallsToMeth(MethodOrMethodContext meth, MethodOrMethodContext callback);

    /**
     * Map from reachable sensitives to sets of callbacks.
     */
    Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap();

    /**
     * To be used for checkers only. this method does not check points-to consistency between parent edges and child
     * edges.
     * <p>
     * For methods executed directly inside callback, parent will be the edge from dummy main to callback.
     */
    Set<Edge> getParentEdges(Edge edge, MethodOrMethodContext callback);
}

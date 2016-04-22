package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/3/2016.
 */
public interface CallPathHolder {

    Set<MethodOrMethodContext> getReachableCallbacks(MethodOrMethodContext sensitive);

    Set<MethodOrMethodContext> getReachableCallbacks();

    Set<Edge> getCallsToSensitiveFor(MethodOrMethodContext callback);

    void printPathsFromCallbackToSensitive();
}

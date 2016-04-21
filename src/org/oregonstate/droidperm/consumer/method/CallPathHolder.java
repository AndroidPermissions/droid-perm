package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/3/2016.
 */
public interface CallPathHolder {

    Set<MethodOrMethodContext> getReachableCallbacks(MethodOrMethodContext sensitive);

    Set<MethodOrMethodContext> getReachableCallbacks();

    void printPathsFromCallbackToSensitive();
}

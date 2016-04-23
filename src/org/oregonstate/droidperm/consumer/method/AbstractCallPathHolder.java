package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/21/2016.
 */
public abstract class AbstractCallPathHolder implements CallPathHolder {
    protected MethodOrMethodContext dummyMainMethod;
    protected Set<MethodOrMethodContext> sensitives;
    private Set<MethodOrMethodContext> reachableCallbacks;

    public AbstractCallPathHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> sensitives) {
        this.sensitives = sensitives;
        this.dummyMainMethod = dummyMainMethod;
    }

    public Set<MethodOrMethodContext> getReachableCallbacks() {
        if (reachableCallbacks == null) {
            reachableCallbacks = new HashSet<>();
            getSensitiveToCallbacksMap().values().stream().forEach(reachableCallbacks::addAll);
        }
        return reachableCallbacks;
    }

    @Override
    public Set<MethodOrMethodContext> getReachableCallbacks(MethodOrMethodContext sensitive) {
        return getSensitiveToCallbacksMap().get(sensitive);
    }

    @Override
    public Set<Edge> getCallsToSensitiveFor(MethodOrMethodContext callback) {
        throw new UnsupportedOperationException();//can be done for each implementation, but differently
    }

    @Override
    public List<Edge> getCallsToMeth(MethodOrMethodContext meth, MethodOrMethodContext callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<MethodOrMethodContext> getReacheableSensitives(Edge edge) {
        throw new UnsupportedOperationException();
    }

    /**
     * Map from sensitives to sets of callbacks.
     */
    protected abstract Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap();
}

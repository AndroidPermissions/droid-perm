package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;

import java.util.HashSet;
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

    /**
     * Map from sensitives to sets of callbacks.
     */
    protected abstract Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap();
}

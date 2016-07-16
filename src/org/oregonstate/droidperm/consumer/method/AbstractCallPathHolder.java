package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.SortUtil;
import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/21/2016.
 */
public abstract class AbstractCallPathHolder implements CallPathHolder {
    protected MethodOrMethodContext dummyMainMethod;
    protected Set<MethodOrMethodContext> sensitives;

    /**
     * Elements are unique
     */
    private List<MethodOrMethodContext> reachableCallbacks;

    public AbstractCallPathHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> sensitives) {
        this.sensitives = sensitives;
        this.dummyMainMethod = dummyMainMethod;
    }

    public List<MethodOrMethodContext> getReachableCallbacks() {
        if (reachableCallbacks == null) {
            reachableCallbacks = getSensitiveToCallbacksMap().values().stream().flatMap(Collection::stream)
                    .distinct().sorted(SortUtil.methodOrMCComparator).collect(Collectors.toList());
        }
        return reachableCallbacks;
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
}

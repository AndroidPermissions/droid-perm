package org.oregonstate.droidperm.unused;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows transitively navigating over source methods of a particular method.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class TransitiveSources {
    private CallGraph cg;
    private Filter filter;

    public TransitiveSources(CallGraph cg) {
        this.cg = cg;
    }

    public TransitiveSources(CallGraph cg, Filter filter) {
        this.cg = cg;
        this.filter = filter;
    }

    public List<MethodOrMethodContext> getInflow(MethodOrMethodContext momc) {
        ArrayList<MethodOrMethodContext> methods = new ArrayList<>();
        Iterator<Edge> it = cg.edgesInto(momc);
        if (filter != null) it = filter.wrap(it);
        while (it.hasNext()) {
            Edge e = it.next();
            methods.add(e.getSrc());
        }
        return getInflow(methods);
    }

    public List<MethodOrMethodContext> getInflow(Collection<MethodOrMethodContext> methods) {
        Set<MethodOrMethodContext> set = new HashSet<>();
        List<MethodOrMethodContext> worklist =
                methods.stream()
                        .filter(set::add) ///stateful lambda, ensures unicity
                        .collect(Collectors.toList());
        return getInflow(set, worklist);
    }

    private List<MethodOrMethodContext> getInflow(Set<MethodOrMethodContext> set,
                                                  List<MethodOrMethodContext> worklist) {
        for (int i = 0; i < worklist.size(); i++) {
            MethodOrMethodContext method = worklist.get(i);
            Iterator<Edge> it = cg.edgesInto(method);
            if (filter != null) it = filter.wrap(it);
            while (it.hasNext()) {
                Edge e = it.next();
                if (set.add(e.getSrc())) worklist.add(e.getSrc());
            }
        }
        return worklist;
    }
}

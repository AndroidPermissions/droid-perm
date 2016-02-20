package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;

import java.util.*;

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
        return getInflow(methods.iterator());
    }

    public List<MethodOrMethodContext> getInflow(Iterator<MethodOrMethodContext> methods) {
        Set<MethodOrMethodContext> s = new HashSet<>();
        List<MethodOrMethodContext> worklist = new ArrayList<>();
        while (methods.hasNext()) {
            MethodOrMethodContext method = methods.next();
            if (s.add(method))
                worklist.add(method);
        }
        return getInflow(s, worklist);
    }

    private List<MethodOrMethodContext> getInflow(Set<MethodOrMethodContext> s,
                                                  List<MethodOrMethodContext> worklist) {
        for (int i = 0; i < worklist.size(); i++) {
            MethodOrMethodContext method = worklist.get(i);
            Iterator<Edge> it = cg.edgesInto(method);
            if (filter != null) it = filter.wrap(it);
            while (it.hasNext()) {
                Edge e = it.next();
                if (s.add(e.getSrc())) worklist.add(e.getSrc());
            }
        }
        return worklist;
    }
}

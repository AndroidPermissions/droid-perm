package org.oregonstate.droidperm.unused;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import soot.MethodOrMethodContext;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Extends the basic call graph with the ability to query edges into /out of a plain SootMethod, even when
 * the graph is context-sensitive.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/7/2016.
 */
public class ContextAwareCallGraph extends CallGraph {

    protected SetMultimap<SootMethod, MethodOrMethodContext> methodToMethodContext = HashMultimap.create();

    //work with: srcMethodToEdge, tgtToEdge
    @Override
    public boolean addEdge(Edge e) {
        if (srcMethodToEdge.get(e.getSrc()) == null) {
            methodToMethodContext.put(e.src(), e.getSrc());
        }
        if (tgtToEdge.get(e.getTgt()) == null) {
            methodToMethodContext.put(e.tgt(), e.getTgt());
        }

        return super.addEdge(e);
    }

    @Override
    public boolean removeEdge(Edge e) {
        boolean res = super.removeEdge(e);

        if (srcMethodToEdge.get(e.getSrc()) == null) {
            methodToMethodContext.remove(e.src(), e.getSrc());
        }
        if (tgtToEdge.get(e.getTgt()) == null) {
            methodToMethodContext.remove(e.tgt(), e.getTgt());
        }

        return res;
    }

    public Iterator<Edge> edgesInto(SootMethod method) {
        return new IteratorOfIterators<MethodOrMethodContext, Edge>(methodToMethodContext.get(method).iterator()) {
            @Override
            public Iterator<Edge> getIteratorFor(MethodOrMethodContext outer) {
                return edgesInto(outer);
            }
        };
    }

    public Iterator<Edge> edgesOutOf(SootMethod method) {
        return new IteratorOfIterators<MethodOrMethodContext, Edge>(methodToMethodContext.get(method).iterator()) {
            @Override
            public Iterator<Edge> getIteratorFor(MethodOrMethodContext outer) {
                return edgesOutOf(outer);
            }
        };
    }

    public Set<MethodOrMethodContext> getNodes(SootMethod method) {
        return Collections.unmodifiableSet(methodToMethodContext.get(method));
    }
}

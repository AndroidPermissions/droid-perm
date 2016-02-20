package org.oregonstate.droidperm.util;

import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

/**
 * Retains all entry point methods - e.g. the dummy main.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class EntryPointPredicate implements EdgePredicate {

    @Override
    public boolean want(Edge e) {
        return e.src() == null;
    }
}

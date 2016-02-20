package org.oregonstate.droidperm.util;

import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

/**
 * Retains all edges whose source has the given signature (which includes target class).
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class FixedSourcePredicate implements EdgePredicate {

    private String signature;

    public FixedSourcePredicate(String signature) {
        this.signature = signature;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean want(Edge e) {
        return e.src() != null && e.src().getSignature().equals(signature);
    }
}

package org.oregonstate.droidperm.util;

import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

/**
 * Retains all edges whose target has the given signature (which includes target class).
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class FixedTargetPredicate implements EdgePredicate {

    private String signature;

    public FixedTargetPredicate(String signature) {
        this.signature = signature;
    }

    @Override
    public boolean want( Edge e ) {
        e.tgt().context();
        return e.tgt().getSignature().equals(signature);
    }
}

package org.oregonstate.droidperm.util;

import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

/**
 * Retains all edges whose target has the given target class and subsignature.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class SpecificTargetPred implements EdgePredicate {

    //todo add to filter logic the target class.

    private String subsignature;

    public SpecificTargetPred(String subsignature) {
        this.subsignature = subsignature;
    }

    @Override
    public boolean want( Edge e ) {
        e.tgt().context();
        return e.tgt().getSubSignature().equals(subsignature);
    }
}

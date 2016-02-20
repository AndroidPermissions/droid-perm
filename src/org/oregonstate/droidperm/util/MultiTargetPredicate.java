package org.oregonstate.droidperm.util;

import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retains all edges whose target is one of the method definitions given in the constructor argument.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/19/2016.
 */
public class MultiTargetPredicate implements EdgePredicate {

    private Set<String> methodSignatures;

    public MultiTargetPredicate(Collection<SootMethodAndClass> methodDefs) {
        methodSignatures = methodDefs.stream().map(SootMethodAndClass::getSignature).collect(Collectors.toSet());
    }

    @Override
    public boolean want(Edge e) {
        //toperf: getSignature() is an expensive operation, should be replaced with something else.
        // Maybe an implementation of equals() on SootMethod? Or a comparator?
        // SootMethodAndClass has a fast equals().
        return methodSignatures.contains(e.tgt().getSignature());
    }
}

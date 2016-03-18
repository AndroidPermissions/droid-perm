package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.toolkits.callgraph.Targets;
import soot.jimple.toolkits.callgraph.TransitiveTargets;

import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/22/2016.
 */
public class DebugUtil {
    public static void printTransitiveTargets(MethodOrMethodContext meth) {
        System.out.println("\nTransitive targets for " + meth);
        StreamUtils.asStream(new TransitiveTargets(Scene.v().getCallGraph()).iterator(meth))
                .forEach(tgt -> System.out.println("  " + tgt));
    }

    public static void printTransitiveTargets(Set<MethodOrMethodContext> consumers) {
        System.out.println("\n\nTransitive targets for each consumer \n====================================");
        consumers.stream().forEach(DebugUtil::printTransitiveTargets);
    }

    public static void printTargets(MethodOrMethodContext meth) {
        System.out.println("\nDirect targets for " + meth);
        StreamUtils.asStream(new Targets(Scene.v().getCallGraph().edgesOutOf(meth)))
                .forEach(tgt -> System.out.println("  " + tgt));
    }

    public static void printTargets(Set<MethodOrMethodContext> consumers) {
        System.out.println("\n\nDirect targets for each consumer \n====================================");
        consumers.stream().forEach(DebugUtil::printTargets);
    }
}

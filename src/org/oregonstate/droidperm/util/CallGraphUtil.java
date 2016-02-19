package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/15/2016.
 */
public class CallGraphUtil {

    //no spaces allowed in the signature format
    public static final String PRODUCER_SIG = "int checkSelfPermission(" +
            "android.content.Context,java.lang.String)";

    public static final String CONSUMER_SIG = "java.lang.String getCachePath(java.net.URL)";

    /**
     * @return an iterator over all methods in the graph that have the given signature.
     */
    public static Iterator<MethodOrMethodContext> iterateMethods(CallGraph cg, String subsignature) {
        return new Targets(new Filter(new SpecificTargetPred(subsignature)).wrap(cg.iterator()));
    }

    /**
     * Get a fixed ethod requiring permissions for test purpose.
     */
    public static MethodOrMethodContext getPermDemandingMethod(String signature) {
        Iterator<MethodOrMethodContext> methIterator = iterateMethods(Scene.v().getCallGraph(), signature);
        MethodOrMethodContext meth = methIterator.hasNext() ? methIterator.next() : null;
        if (meth == null || methIterator.hasNext()) {
            throw new RuntimeException("Either 0 or >1 methods found for sig: " + signature);
        }
        return meth;
    }

    public static Iterator<MethodOrMethodContext> getInflowCallGraph(String signature) {
        return new TransitiveSources(Scene.v().getCallGraph()).iterator(getPermDemandingMethod(signature));
    }

    public static Set<MethodOrMethodContext> getInflowIntersection(String producerSig, String consumerSig){
        Set<MethodOrMethodContext> prodSet = new HashSet<>();
        Iterator<MethodOrMethodContext> prodInflow = getInflowCallGraph(producerSig);
        while (prodInflow.hasNext()) {
            prodSet.add(prodInflow.next());
        }

        Set<MethodOrMethodContext> consSet = new HashSet<>();
        Iterator<MethodOrMethodContext> consInflow = getInflowCallGraph(consumerSig);
        while (consInflow.hasNext()) {
            consSet.add(consInflow.next());
        }
        prodSet.retainAll(consSet);
        return prodSet;
    }

    public static void printTestInflow() {
        printInflow(PRODUCER_SIG);
        printInflow(CONSUMER_SIG);
        printCommonSet();
    }

    private static void printInflow(String consumerSig) {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph of " + consumerSig + ":\n");

        Iterator<MethodOrMethodContext> inflow = getInflowCallGraph(consumerSig);
        while (inflow.hasNext()) {
            System.out.println(inflow.next());
        }
    }

    private static void printCommonSet() {
        System.out.println("\n============================================");
        System.out.println("Inflow intersection:\n");

        Set<MethodOrMethodContext> inflow = getInflowIntersection(PRODUCER_SIG, CONSUMER_SIG);
        for (MethodOrMethodContext meth : inflow) {
            System.out.println(meth);
        }
    }
}

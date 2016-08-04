package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.toolkits.scalar.Pair;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.Collection;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/4/2016.
 */
public class UndetectedItemsUtil {

    /**
     * @param outflowIgnoreSet - entries from this list are used to filter out call contexts. Works for
     *                         TwilightManager.
     */
    private static MultiMap<SootMethod, Pair<Stmt, SootMethod>> getUndetectedCalls(
            Collection<? extends SootMethodAndClass> methodDefs, Set<MethodOrMethodContext> detected,
            Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> undetected = SceneUtil.resolveMethodUsages(methodDefs);
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> copy = new HashMultiMap<>(undetected);

        //filter out ignored
        for (SootMethod sens : copy.keySet()) {
            copy.get(sens).stream().filter(pair -> outflowIgnoreSet.contains(pair.getO2()))
                    .forEach(pair -> undetected.remove(sens, pair));
        }

        //filter out detected
        CallGraph cg = Scene.v().getCallGraph();
        detected.stream().flatMap(detMeth -> StreamUtil.asStream(cg.edgesInto(detMeth))).forEach(
                edge -> undetected.remove(edge.tgt(), new Pair<>(edge.srcStmt(), edge.src())));
        return undetected;
    }

    public static void printUndetectedCheckers(Collection<? extends SootMethodAndClass> methodDefs,
                                               Set<MethodOrMethodContext> detected, Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> undetectedCheckers =
                getUndetectedCalls(methodDefs, detected, outflowIgnoreSet);

        System.out.println("\n\nUndetected checkers \n"
                + "========================================================================");
        for (SootMethod checker : undetectedCheckers.keySet()) {
            System.out.println(checker);
            for (Pair<Stmt, SootMethod> call : undetectedCheckers.get(checker)) {
                System.out.println("\tfrom " + call.getO2() + " : " + call.getO1().getJavaSourceStartLineNumber());
            }
        }
        System.out.println("\nUndetected checkers execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }
}

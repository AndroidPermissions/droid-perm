package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/15/2016.
 */
public class CallGraphUtil {

    private static Map<Unit, SootMethod> stmtToMethodMap;

    public static <T extends SootMethodAndClass> Map<T, Set<MethodOrMethodContext>> resolveCallGraphEntriesToMap(
            Collection<T> methodDefs) {
        Map<T, Set<MethodOrMethodContext>> result = new HashMap<>();
        for (T methodDef : methodDefs) {
            Set<MethodOrMethodContext> methods = getNodesFor(HierarchyUtil.resolveAbstractDispatch(methodDef));

            if (!methods.isEmpty()) {
                result.put(methodDef, methods);
            }
        }
        return result;
    }

    /**
     * Return the subset of methods (eventually in their context) contained in the call graph, out of the input
     * collection.
     *
     * @param methods - input collection
     */
    public static Set<MethodOrMethodContext> getNodesFor(Collection<SootMethod> methods) {
        return methods.stream().map(CallGraphUtil::getNodesFor)
                .collect(MyCollectors.toFlatSet()); //Collect MoMC sets for every method into one set
    }

    public static Set<MethodOrMethodContext> getNodesFor(SootMethod method) {
        CallGraph callGraph = Scene.v().getCallGraph();
        if (callGraph instanceof ContextAwareCallGraph) {
            ContextAwareCallGraph caCallGraph = (ContextAwareCallGraph) callGraph;
            return caCallGraph.getNodes(method);
        } else {
            return callGraph.edgesInto(method).hasNext() || callGraph.edgesOutOf(method).hasNext()
                    ? Collections.singleton(method) : Collections.emptySet();
        }
    }

    public static MethodOrMethodContext getEntryPointMethod(SootMethod method) {
        Set<MethodOrMethodContext> allMatches = getNodesFor(method);
        if (allMatches.isEmpty()) {
            throw new RuntimeException("Entry point method not found: " + method);
        } else if (allMatches.size() > 1) {
            throw new RuntimeException("More than one call graph node found for: " + method);
        } else {
            return allMatches.iterator().next();
        }
    }

    public static List<MethodOrMethodContext> getInflow(Set<MethodOrMethodContext> methods) {
        return new TransitiveSources(Scene.v().getCallGraph()).getInflow(methods);
    }

    public static List<Edge> getInflowCallGraph(MethodOrMethodContext method) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(Collections.singletonList(method));
    }

    public static List<Edge> getInflowCallGraph(Set<MethodOrMethodContext> methods) {
        return new InflowBuilder(Scene.v().getCallGraph()).getInflow(methods);
    }

    public static MethodOrMethodContext getContainingMethod(Unit unit) {
        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> edgeIterator = cg.edgesOutOf(unit);
        return edgeIterator.hasNext() ? edgeIterator.next().getSrc() : null;
    }

    /**
     * Algorithm to infer the containing method for each statement in a dataflow.
     * <p>
     * For each statement in the dataflow:
     * <p>
     * Case 1. If it has outbound edges in the call graph, pick any of them and check this statement in the source
     * method.
     * <p>
     * Case 2. If it has no edges in the call graph, then it's likely not a method call. It could be an assignment or
     * return in a method called before. If previous statement has outbound methods, then previous statement is a method
     * call calling the desired method. Search method bodies of all targets of the previous statement. One of them
     * should contain this Stmt.
     * <p>
     * Case 3. If a container is still not found, traverse the statements backwards and every time check this statement
     * in the containing method of past statements.
     * <p>
     * Case 4. If still not container found, return null.
     * <p>
     * In all cases, a method will be considered container for this statement only if its body contains the statement.
     * <p>
     * Additional rule for indent: if previous statement was a return, then indent for this statement is one level below
     * the previous one.
     */
    public static Map<Stmt, Pair<MethodOrMethodContext, Integer>> resolveContainersForDataflow(Stmt[] dataflow) {
        CallGraph cg = Scene.v().getCallGraph();
        boolean[] hasEdges = new boolean[dataflow.length];
        Map<Stmt, Pair<MethodOrMethodContext, Integer>> result = new HashMap<>();

        for (int index = 0; index < dataflow.length; index++) {
            Stmt stmt = dataflow[index];
            int i = index;

            Supplier<Pair<MethodOrMethodContext, Integer>> containerCheckAlg = () -> {
                Iterator<Edge> edgeIterator = cg.edgesOutOf(stmt);
                hasEdges[i] = edgeIterator.hasNext();

                if (i > 0 && hasEdges[i - 1]) { // case 2
                    Iterator<Edge> prevEdgeIterator = cg.edgesOutOf(dataflow[i - 1]);
                    Edge lastCallEdge = StreamUtil.asStream(prevEdgeIterator)
                            .filter(edge -> edge.tgt().hasActiveBody() &&
                                    edge.tgt().getActiveBody().getUnits().contains(stmt))
                            .findAny().orElse(null);
                    if (lastCallEdge != null) {
                        MethodOrMethodContext tgt = lastCallEdge.getTgt();
                        return new Pair<>(tgt, getNewIndent(i, dataflow, result, true));
                    }
                }

                if (hasEdges[i]) { //case 1
                    MethodOrMethodContext possibleCont = edgeIterator.next().getSrc();
                    if (possibleCont.method().getActiveBody().getUnits().contains(stmt)) {
                        return new Pair<>(possibleCont, getNewIndent(i, dataflow, result, false));
                    }
                }

                //case 3 and 4
                int j = i - 1;
                while (j >= 0 && result.get(dataflow[j]) != null && result.get(dataflow[j]).getO1() != null
                        && !result.get(dataflow[j]).getO1().method().getActiveBody().getUnits().contains(stmt)) {
                    j--;
                }
                return j >= 0 ? result.get(dataflow[j])
                        //case 4 - use stmtToMethodMap to infer container method
                        : new Pair<>(getStmtToMethodMap().get(stmt), getNewIndent(i, dataflow, result, false));
            };

            result.put(stmt, containerCheckAlg.get());
        }
        normalizeIndents(result);
        return result;
    }

    /**
     * Normalizing all indents so that the minimal will be 0.
     */
    private static void normalizeIndents(Map<Stmt, Pair<MethodOrMethodContext, Integer>> result) {
        int minIndent = result.values().stream().map(Pair::getO2).min(Comparator.naturalOrder()).orElse(0);

        /*The same value (method) might repeat several times.
        If we don't use distinct() it will be "normalized" more than once.
        Stream mutability issue:
        If I don't collect to list first, map will be messed up when I try to alter its values, and distinct()
         won't work correctly.

        Cause of this problem: It is prohibited to modify set elements in a way that affects equals()
         http://stackoverflow.com/questions/19589864/hashset-behavior-when-changing-field-value
        */

        /*Computing the map of unique values (pairs). It is possible that multiple keys are mapped to the same pair.
        Also it is possible that 2 keys are mapped to distinct instances of Pair which are nevertheless equal
         (according to equals()).*/
        Map<Pair<MethodOrMethodContext, Integer>, Pair<MethodOrMethodContext, Integer>> valuesToValues =
                result.values().stream().distinct().collect(Collectors.toMap(pair -> pair, pair -> pair));

        //Normalizing result map values. Replacing multiple instances corresponding to same pair with one instance.
        result.keySet().stream().forEach(key -> result.put(key, valuesToValues.get(result.get(key))));

        //Normalizing indents. Increasing all indents so that the minimal one is always 0.
        valuesToValues.keySet().forEach(pair -> pair.setO2(pair.getO2() - minIndent));
    }

    private static int getNewIndent(int stmtIndex, Stmt[] dataflow,
                                    Map<Stmt, Pair<MethodOrMethodContext, Integer>> result, boolean afterMethodCall) {
        if (stmtIndex == 0) {
            return 0;
        } else if (afterMethodCall) {
            return result.get(dataflow[stmtIndex - 1]).getO2() + 1;
        } else if (dataflow[stmtIndex - 1] instanceof ReturnStmt || dataflow[stmtIndex - 1] instanceof ReturnVoidStmt) {
            return result.get(dataflow[stmtIndex - 1]).getO2() - 1;
        } else {
            return result.get(dataflow[stmtIndex - 1]).getO2();
        }
    }

    /**
     * Builds a map from all statements in the analysis classpath to their containing methods. Only methods reachable
     * through the call graph are included in this map.
     */
    private static Map<Unit, SootMethod> buildStmtToMethodMap() {
        try {
            Class<?> cgClazz = CallGraph.class;
            Field tgtToEdgeField = cgClazz.getDeclaredField("tgtToEdge");
            tgtToEdgeField.setAccessible(true);

            //this collection is taken from CallGraph internals.
            @SuppressWarnings("unchecked")
            Map<MethodOrMethodContext, Edge> tgtToEdge =
                    (Map<MethodOrMethodContext, Edge>) tgtToEdgeField.get(Scene.v().getCallGraph());

            Map<Unit, SootMethod> result;
            result = tgtToEdge.keySet().stream().map(MethodOrMethodContext::method)
                    .filter(SootMethod::hasActiveBody) //only SootMethod in CG with active body here
                    .collect(HashMap::new, (Map<Unit, SootMethod> map, SootMethod meth)
                                    -> meth.getActiveBody().getUnits().forEach(unit -> map.put(unit, meth)),
                            StreamUtil::mutableMapCombiner);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a map from all statements in the analysis classpath to their containing methods. Only methods reachable
     * through the call graph are included in this map.
     */
    public static Map<Unit, SootMethod> getStmtToMethodMap() {
        if (stmtToMethodMap == null) {
            stmtToMethodMap = buildStmtToMethodMap();
        }
        return stmtToMethodMap;
    }
}

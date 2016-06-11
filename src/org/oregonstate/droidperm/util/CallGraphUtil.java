package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.unused.ContextAwareCallGraph;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/15/2016.
 */
public class CallGraphUtil {

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
     */
    public static Map<Stmt, Pair<MethodOrMethodContext, String>> resolveContainersForDataflow(Stmt[] dataflow) {
        CallGraph cg = Scene.v().getCallGraph();
        boolean[] hasEdges = new boolean[dataflow.length];
        Map<MethodOrMethodContext, String> indent = new HashMap<>();
        Map<Stmt, Pair<MethodOrMethodContext, String>> result = new HashMap<>();

        for (int index = 0; index < dataflow.length; index++) {
            Stmt stmt = dataflow[index];
            int i = index;

            Supplier<Pair<MethodOrMethodContext, String>> containerCheckAlg = () -> {
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
                        indent.put(tgt, getIndent(lastCallEdge.getSrc(), indent) + "\t");
                        return new Pair<>(tgt, indent.get(tgt));
                    }
                }

                if (hasEdges[i]) { //case 1
                    MethodOrMethodContext possibleCont = edgeIterator.next().getSrc();
                    if (possibleCont.method().getActiveBody().getUnits().contains(stmt)) {
                        return new Pair<>(possibleCont, getIndent(possibleCont, indent));
                    }
                }

                //case 3 and 4
                int j = i - 1;
                while (j >= 0 && result.get(dataflow[j]) != null
                        && !result.get(dataflow[j]).getO1().method().getActiveBody().getUnits().contains(stmt)) {
                    j--;
                }
                return j >= 0 ? result.get(dataflow[j]) : new Pair<>(null, "");
            };

            result.put(stmt, containerCheckAlg.get());
        }
        return result;
    }

    private static String getIndent(MethodOrMethodContext src, Map<MethodOrMethodContext, String> indent) {
        indent.putIfAbsent(src, "");
        return indent.get(src);
    }
}

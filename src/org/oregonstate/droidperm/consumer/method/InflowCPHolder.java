package org.oregonstate.droidperm.consumer.method;

import org.oregonstate.droidperm.util.CallGraphUtil;
import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/3/2016.
 */
public class InflowCPHolder extends AbstractCallPathHolder {

    /**
     * 1st level map: key = consumer, value = inflow graph as 2nd lvl map. <br/> 2nd level map: key = source node, value
     * = list of edges having that source node.
     */
    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, List<Edge>>> consumerToInflowGraphMap;

    /**
     * Map from sensitives to sets of callbacks.
     */
    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> consumerCallbacks;

    public InflowCPHolder(MethodOrMethodContext dummyMainMethod, Set<MethodOrMethodContext> consumers) {
        super(dummyMainMethod, consumers);
        consumerToInflowGraphMap = buildConsumerToInflowGraphMap();
        consumerCallbacks = buildConsumerCallbacksFromInflows();
    }

    private Map<MethodOrMethodContext, Map<MethodOrMethodContext, List<Edge>>> buildConsumerToInflowGraphMap() {
        return sensitives.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> CallGraphUtil.getInflowCallGraph(consumer).stream()
                        .filter(edge -> edge.getSrc() != null) //basically elliminates the main method.
                        .collect(Collectors.groupingBy(Edge::getSrc))
        ));
    }

    private Map<MethodOrMethodContext, Set<MethodOrMethodContext>> buildConsumerCallbacksFromInflows() {
        return sensitives.stream().collect(Collectors.toMap(
                consumer -> consumer,
                consumer -> consumerToInflowGraphMap.get(consumer)
                        .get(dummyMainMethod).stream() // list of edges coming from main in this inflow
                        .map(Edge::getTgt) //all targets of main method inside this inflow
                        .collect(Collectors.toSet())
        ));
    }

    void printConsumerInflows() {
        System.out.println("\n============================================");
        System.out.println("Inflow call graph for sensitives\n");

        for (MethodOrMethodContext meth : sensitives) {
            System.out.println("\nConsumer " + meth + " :\n");
            consumerToInflowGraphMap.get(meth).forEach(
                    (src, edges) -> {
                        System.out.println("From " + src + " to:");
                        edges.stream().forEach(edge -> System.out.println("    " + edge.getTgt()));
                    });
        }
    }

    @Override
    public void printPathsFromCallbackToSensitive() {
        System.out.println("\nPaths from each callback to each consumer");
        System.out.println("============================================\n");

        for (MethodOrMethodContext cons : sensitives) {
            Map<MethodOrMethodContext, List<Edge>> inflow = consumerToInflowGraphMap.get(cons);
            for (MethodOrMethodContext callback : consumerCallbacks.get(cons)) {
                printPath(callback, cons, inflow);
            }
        }
    }

    private void printPath(MethodOrMethodContext src, MethodOrMethodContext dest,
                           Map<MethodOrMethodContext, List<Edge>> inflow) {
        List<MethodOrMethodContext> path = computePathFromInflow(src, dest, inflow);

        System.out.println("From " + src + "\n  to " + dest);
        System.out.println("--------------------------------------------");
        if (path != null) {
            path.forEach(System.out::println);
        } else {
            System.out.println("Not found!");
        }
        System.out.println();
    }

    /**
     * Compute the path forward from src node to dest node through the given inflow.
     *
     * @param inflow - a map from nodes to lists of edges starting from that node.
     * @return the path.
     */
    List<MethodOrMethodContext> computePathFromInflow(MethodOrMethodContext src, MethodOrMethodContext dest,
                                                      Map<MethodOrMethodContext, List<Edge>> inflow) {
        MethodOrMethodContext node = src;

        //required to prevent infinite loops in case inflow contains recursion
        Set<Edge> traversed = new HashSet<>();
        List<MethodOrMethodContext> path = new ArrayList<>();
        while (node != null & node != dest) {
            path.add(node);
            Edge nextEdge =
                    inflow.get(node).stream().filter(edge -> !traversed.contains(edge)).findAny().orElse(null);
            if (nextEdge != null) {
                traversed.add(nextEdge);
                node = nextEdge.getTgt();
            } else {
                node = null;
            }
        }
        if (node != null) {
            path.add(node);
        } else {
            return null;
        }
        return path;
    }

    @Override
    public Map<MethodOrMethodContext, Set<MethodOrMethodContext>> getSensitiveToCallbacksMap() {
        return consumerCallbacks;
    }
}

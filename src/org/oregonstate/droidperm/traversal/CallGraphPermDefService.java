package org.oregonstate.droidperm.traversal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.istack.internal.Nullable;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 12/14/2016.
 */
public class CallGraphPermDefService {

    private static final Logger logger = LoggerFactory.getLogger(CallGraphPermDefService.class);

    private ScenePermissionDefService scenePermDef;

    public CallGraphPermDefService(ScenePermissionDefService scenePermDef) {
        this.scenePermDef = scenePermDef;
    }

    public Set<String> getPermissionsFor(Collection<Edge> sensEdges) {
        return sensEdges.stream().map(this::getPermissionsFor).collect(MyCollectors.toFlatSet());
    }

    @Nullable
    public Set<String> getPermissionsFor(Edge sensEdge) {
        Set<String> methodSensPerm = scenePermDef.getPermissionsFor(sensEdge.tgt());
        if (!methodSensPerm.isEmpty()) {
            return methodSensPerm;
        }

        //Maybe this edge is a parametric sensitive.
        List<SootField> sensArguments = getSensitiveArgValues(sensEdge);
        Set<Set<String>> permSets = sensArguments.stream().map(scenePermDef::getPermissionsFor)
                .filter(set -> !set.isEmpty())
                .collect(Collectors.toSet());
        if (permSets.size() > 1) {
            logger.warn("Edge has multiple permission sets: " + permSets + ".\nEdge is " + sensEdge);
        }
        return permSets.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

    /**
     * Only to be called for edges representing parametric sensitives. Returns null if this is a parametric sensitive
     * but the argument is not a sensitive field.
     */
    public List<SootField> getSensitiveArgValues(Edge sensEdge) {
        Stmt srcStmt = sensEdge.srcStmt();
        assert srcStmt != null; //sensitive edges always come from method calls
        Value sensArg = srcStmt.getInvokeExpr().getArg(scenePermDef.getSensitiveArgumentIndex(sensEdge.tgt()));
        List<Value> argValues;
        if (sensArg instanceof Local) {
            //noinspection ConstantConditions
            ExceptionalUnitGraph graph = new ExceptionalUnitGraph(sensEdge.src().retrieveActiveBody());
            SmartLocalDefs localDefs = new SmartLocalDefs(graph, new SimpleLiveLocals(graph));
            List<Unit> argAssignPoints = localDefs.getDefsOfAt((Local) sensArg, srcStmt);
            argValues = argAssignPoints.stream().map(unit -> ((DefinitionStmt) unit).getRightOp())
                    .collect(Collectors.toList());
        } else {
            argValues = Collections.singletonList(sensArg);
        }
        return argValues.stream().map(value -> {
            if (value instanceof FieldRef) {
                return ((FieldRef) value).getField();
            } else if (value instanceof StringConstant) {
                return scenePermDef.getFieldFor(((StringConstant) value).value);
            } else if (value instanceof NullConstant) {
                return null;
            } else {
                logger.warn("Parametric sensitive " + sensEdge + " has a parameter with unsupported value: "
                        + value);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public LinkedHashSet<Edge> buildSensEdges() {
        List<SootMethod> sceneMethodSensitives = scenePermDef.getSceneMethodSensitives();
        List<SootMethod> sceneParametricSensitives = scenePermDef.getSceneParametricSensitives();
        List<SootMethod> scenePresensitiveMethods = Lists.newArrayList(
                Iterables.concat(sceneMethodSensitives, sceneParametricSensitives));

        return CallGraphUtil.getEdgesInto(scenePresensitiveMethods).stream() //already sorted
                .filter(edge -> !getPermissionsFor(edge).isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void printSensitiveHeader(MethodOrMethodContext sens, String prefix) {
        System.out.println(prefix + (scenePermDef.isParametric(sens) ? "Parametric sensitive " : "Sensitive ") + sens);
    }

    public void printSensitiveContext(Edge sensEdge, String prefix) {
        System.out.println(prefix + "context " + PrintUtil.toMethodLogString(sensEdge.srcStmt()));
        if (scenePermDef.isParametric(sensEdge.tgt())) {
            System.out.println(prefix + "param " + getSensitiveArgValues(sensEdge));
        }
    }
}

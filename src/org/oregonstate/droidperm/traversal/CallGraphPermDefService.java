package org.oregonstate.droidperm.traversal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.istack.internal.Nullable;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import soot.MethodOrMethodContext;
import soot.SootField;
import soot.SootMethod;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 12/14/2016.
 */
public class CallGraphPermDefService {

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
        SootField sensArgument = getSensitiveArgument(sensEdge);
        return scenePermDef.getPermissionsFor(sensArgument);
    }

    /**
     * fixme full support for field sensitives
     * <p>
     * Only to be called for edges representing parametric sensitives. Returns null if this is a parametric sensitive
     * but the argument is not a sensitive field.
     */
    public SootField getSensitiveArgument(Edge sensEdge) {
        Stmt srcStmt = sensEdge.srcStmt();
        assert srcStmt != null; //sensitive edges always come from method calls
        Value arg0 = srcStmt.getInvokeExpr().getArg(0);
        if (arg0 instanceof StringConstant) {
            return scenePermDef.getFieldFor(((StringConstant) arg0).value);
        } else {
            throw new RuntimeException("Unsupported arg0 for parametric sensitive: " + arg0);
        }
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
            System.out.println(prefix + "param " + getSensitiveArgument(sensEdge));
        }
    }
}

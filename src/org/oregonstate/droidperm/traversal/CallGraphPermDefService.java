package org.oregonstate.droidperm.traversal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.istack.internal.Nullable;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.util.CallGraphUtil;
import org.oregonstate.droidperm.util.MyCollectors;
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

    /**
     * fixme full support for field sensitives
     * <p>
     * Right now adding only parametric methods
     */
    @Nullable
    public Set<String> getPermissionsFor(Edge sensEdge) {
        Set<String> methodSensPerm = scenePermDef.getPermissionsFor(sensEdge.tgt());
        if (!methodSensPerm.isEmpty()) {
            return methodSensPerm;
        }

        //Maybe this edge is a parametric sensitive.
        Stmt srcStmt = sensEdge.srcStmt();
        assert srcStmt != null; //sensitive edges always come from method calls
        Value arg0 = srcStmt.getInvokeExpr().getArg(0);
        if (arg0 instanceof StringConstant) {
            String argValue = ((StringConstant) arg0).value;
            return scenePermDef.getPermissionsFor(scenePermDef.getFieldFor(argValue));
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
}

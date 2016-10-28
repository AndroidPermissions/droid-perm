package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.util.HierarchyUtil;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All Permission, sensitive and checker-related data structures at scene level.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class ScenePermissionDefService {
    private IPermissionDefProvider permissionDefProvider;

    public ScenePermissionDefService(IPermissionDefProvider permissionDefProvider) {
        this.permissionDefProvider = permissionDefProvider;
    }

    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return permissionDefProvider.getPermCheckerDefs();
    }

    public Set<AndroidMethod> getMethodSensitiveDefs() {
        return permissionDefProvider.getMethodSensitiveDefs();
    }

    public List<SootMethod> getPermCheckers() {
        return HierarchyUtil.resolveAbstractDispatches(permissionDefProvider.getPermCheckerDefs());
    }

    public List<SootMethod> getSceneMethodSensitives() {
        return HierarchyUtil.resolveAbstractDispatches(permissionDefProvider.getMethodSensitiveDefs());
    }

    public Map<AndroidMethod, List<SootMethod>> getSceneSensitivesMap() {
        return HierarchyUtil.resolveAbstractDispatchesToMap(getMethodSensitiveDefs());
    }
}

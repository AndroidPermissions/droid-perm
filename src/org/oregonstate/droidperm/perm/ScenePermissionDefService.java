package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.util.HierarchyUtil;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.toolkits.scalar.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All Permission, sensitive and checker-related data structures at scene level.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class ScenePermissionDefService {

    private Set<SootMethodAndClass> permCheckerDefs;
    private Set<AndroidMethod> methodSensitiveDefs;
    private Map<Set<String>, List<SootMethod>> permissionToSensitivesMap;
    private Map<SootMethod, Set<String>> sensitivesToPermissionsMap;

    public ScenePermissionDefService(IPermissionDefProvider permissionDefProvider) {
        permCheckerDefs = permissionDefProvider.getPermCheckerDefs();
        methodSensitiveDefs = permissionDefProvider.getMethodSensitiveDefs();
        permissionToSensitivesMap = buildPermissionToSensitivesMap();
        sensitivesToPermissionsMap = buildSensitivesToPermissionsMap();
    }

    private Map<AndroidMethod, List<SootMethod>> getSceneSensitivesMap() {
        return HierarchyUtil.resolveAbstractDispatchesToMap(methodSensitiveDefs);
    }

    private Map<SootMethod, Set<String>> buildSensitivesToPermissionsMap() {
        return permissionToSensitivesMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(sm -> new Pair<>(sm, entry.getKey())))
                .collect(Collectors.toMap(Pair::getO1, Pair::getO2));
    }

    private Map<Set<String>, List<SootMethod>> buildPermissionToSensitivesMap() {
        Map<Set<String>, List<AndroidMethod>> permissionToSensitiveDefMap = methodSensitiveDefs
                .stream().collect(Collectors.groupingBy(AndroidMethod::getPermissions));
        return permissionToSensitiveDefMap.keySet().stream()
                .collect(Collectors.toMap(
                        permSet -> permSet,
                        permSet -> permissionToSensitiveDefMap.get(permSet).stream()
                                .flatMap(androMeth -> getSceneSensitivesMap().get(androMeth).stream())
                                .collect(Collectors.toList())
                ));
    }

    public List<SootMethod> getPermCheckers() {
        return HierarchyUtil.resolveAbstractDispatches(permCheckerDefs);
    }

    public List<SootMethod> getSceneMethodSensitives() {
        return HierarchyUtil.resolveAbstractDispatches(methodSensitiveDefs);
    }

    /**
     * @return all sets of permissions for which there is a sensitive definition.
     */
    public Set<Set<String>> getPermissionSets() {
        return methodSensitiveDefs.stream().map(AndroidMethod::getPermissions).collect(Collectors.toSet());
    }

    public Set<String> getPermissionsFor(SootMethod meth) {
        return sensitivesToPermissionsMap.get(meth);
    }

    public List<SootMethod> getSensitivesFor(Set<String> permSet) {
        return permissionToSensitivesMap.get(permSet);
    }
}

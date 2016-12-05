package org.oregonstate.droidperm.scene;

import org.oregonstate.droidperm.perm.FieldSensitiveDef;
import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.StreamUtil;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.options.Options;
import soot.toolkits.scalar.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * All Permission, sensitive and checker-related data structures at scene level.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class ScenePermissionDefService {

    private Set<SootMethodAndClass> permCheckerDefs;
    private Set<AndroidMethod> methodSensitiveDefs;
    private Set<FieldSensitiveDef> fieldSensitiveDefs;
    private Map<Set<String>, Set<SootMethod>> permissionToSensitivesMap;
    private Map<SootMethod, Set<String>> sensitivesToPermissionsMap;
    /**
     * Only sensitive defs that are really found in scene are contained here
     */
    private Map<FieldSensitiveDef, SootField> sceneFieldSensMap;
    private Map<Set<String>, List<SootField>> permissionToFieldSensMap;
    private Map<AndroidMethod, List<SootMethod>> sceneMethodSensMap;

    public ScenePermissionDefService(IPermissionDefProvider permissionDefProvider) {
        permCheckerDefs = permissionDefProvider.getPermCheckerDefs();
        methodSensitiveDefs = permissionDefProvider.getMethodSensitiveDefs();
        fieldSensitiveDefs = permissionDefProvider.getFieldSensitiveDefs();
        sceneMethodSensMap = buildSceneMethodSensMap();
        permissionToSensitivesMap = buildPermissionToSensitivesMap();
        sensitivesToPermissionsMap = buildSensitivesToPermissionsMap();
        sceneFieldSensMap = buildSceneFieldSensMap();
        permissionToFieldSensMap = buildPermissionToFieldSensMap();
    }

    public List<SootMethod> getPermCheckers() {
        return HierarchyUtil.resolveAbstractDispatches(permCheckerDefs);
    }

    private Map<AndroidMethod, List<SootMethod>> buildSceneMethodSensMap() {
        return HierarchyUtil.resolveAbstractDispatchesToMap(methodSensitiveDefs);
    }

    private Map<SootMethod, Set<String>> buildSensitivesToPermissionsMap() {
        return permissionToSensitivesMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(sootMeth -> new Pair<>(sootMeth, entry.getKey())))
                .collect(Collectors.toMap(Pair::getO1, Pair::getO2));
    }

    private Map<Set<String>, Set<SootMethod>> buildPermissionToSensitivesMap() {
        Map<Set<String>, List<AndroidMethod>> permissionToSensitiveDefMap = methodSensitiveDefs
                .stream().collect(Collectors.groupingBy(AndroidMethod::getPermissions));
        return permissionToSensitiveDefMap.keySet().stream()
                .collect(Collectors.toMap(
                        permSet -> permSet,
                        permSet -> permissionToSensitiveDefMap.get(permSet).stream()
                                .flatMap(androMeth -> sceneMethodSensMap.get(androMeth).stream())
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                ));
    }

    public List<SootMethod> getSceneMethodSensitives() {
        return HierarchyUtil.resolveAbstractDispatches(methodSensitiveDefs, true);
    }

    /**
     * @return all sets of permissions for which there is a sensitive method definition.
     */
    public Set<Set<String>> getMethodPermissionSets() {
        return methodSensitiveDefs.stream().map(AndroidMethod::getPermissions).collect(Collectors.toSet());
    }

    public Set<SootMethod> getMethodSensitivesFor(Set<String> permSet) {
        return permissionToSensitivesMap.get(permSet);
    }

    private Map<FieldSensitiveDef, SootField> buildSceneFieldSensMap() {
        Scene scene = Scene.v();

        //Workaround: Classes with intents might not be resolved at this point, due to bytecode constant inlining.
        Options.v().set_ignore_resolving_levels(true);
        fieldSensitiveDefs.stream()
                //filter out classes not in the classpath
                .filter(def -> SourceLocator.v().getClassSource(def.getClassName()) != null)
                .forEach(def -> scene.loadClassAndSupport(def.getClassName()));
        Options.v().set_allow_phantom_refs(false);//may be messed up by the line above

        return fieldSensitiveDefs.stream()
                .filter(def -> scene.containsClass(def.getClassName())
                        && scene.getSootClass(def.getClassName()).getFieldByNameUnsafe(def.getName()) != null)
                .collect(Collectors.toMap(
                        fieldDef -> fieldDef,
                        fieldDef -> scene.getSootClass(fieldDef.getClassName()).getFieldByName(fieldDef.getName()),
                        StreamUtil.throwingMerger(),
                        LinkedHashMap::new
                ));
    }

    private Map<Set<String>, List<SootField>> buildPermissionToFieldSensMap() {
        Map<Set<String>, List<FieldSensitiveDef>> permissionToFieldSensDefMap = fieldSensitiveDefs.stream()
                .collect(Collectors.groupingBy(FieldSensitiveDef::getPermissions));
        return permissionToFieldSensDefMap.keySet().stream()
                .collect(Collectors.toMap(
                        permSet -> permSet,
                        permSet -> permissionToFieldSensDefMap.get(permSet).stream()
                                .map(sceneFieldSensMap::get)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                ));
    }

    public Collection<SootField> getSceneFieldSensitives() {
        return sceneFieldSensMap.values();
    }

    public List<SootField> getFieldSensitivesFor(Set<String> permSet) {
        return permissionToFieldSensMap.get(permSet);
    }

    /**
     * @return all sets of permissions for which there is a sensitive field definition.
     */
    public Set<Set<String>> getFieldPermissionSets() {
        return permissionToFieldSensMap.keySet();
    }

    public Set<Set<String>> getAllPermissionSets() {
        Set<Set<String>> result = new HashSet<>();
        result.addAll(getMethodPermissionSets());
        result.addAll(permissionToFieldSensMap.keySet());
        return result;
    }

    public Set<String> getPermissionsFor(SootMethod meth) {
        return sensitivesToPermissionsMap.get(meth);
    }

    public AndroidMethod getPermDefFor(SootMethod meth) {
        return sceneMethodSensMap.keySet().stream().filter(sensDef -> sceneMethodSensMap.get(sensDef).contains(meth))
                .findAny().orElse(null);
    }

    public FieldSensitiveDef getPermDefFor(SootField field) {
        return sceneFieldSensMap.keySet().stream().filter(sensDef -> sceneFieldSensMap.get(sensDef).equals(field))
                .findAny().orElse(null);
    }
}

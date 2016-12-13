package org.oregonstate.droidperm.scene;

import com.google.common.collect.*;
import org.oregonstate.droidperm.perm.FieldSensitiveDef;
import org.oregonstate.droidperm.perm.IPermissionDefProvider;
import org.oregonstate.droidperm.util.HierarchyUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.StreamUtil;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.options.Options;

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
    private ListMultimap<AndroidMethod, SootMethod> sceneMethodSensMap;
    private SetMultimap<Set<String>, SootMethod> permissionToSensitivesMap;
    private SetMultimap<SootMethod, String> sensitivesToPermissionsMap;
    /**
     * Only sensitive defs that are really found in scene are contained here
     */
    private Map<FieldSensitiveDef, SootField> sceneFieldSensMap;
    private ListMultimap<Set<String>, SootField> permissionToFieldSensMap;
    private final SetMultimap<SootField, String> fieldSensToPermissionsMap;

    private final Set<SootMethodAndClass> parametricSensDef = ImmutableSet.<SootMethodAndClass>builder()
            .add(new SootMethodAndClass("<init>", "android.content.Intent", "void",
                    Collections.singletonList("java.lang.String")))
            .build();

    public ScenePermissionDefService(IPermissionDefProvider permissionDefProvider) {
        permCheckerDefs = permissionDefProvider.getPermCheckerDefs();
        methodSensitiveDefs = permissionDefProvider.getMethodSensitiveDefs();
        fieldSensitiveDefs = permissionDefProvider.getFieldSensitiveDefs();
        sceneMethodSensMap = buildSceneMethodSensMap();
        permissionToSensitivesMap = buildPermissionToSensitivesMap();
        sensitivesToPermissionsMap = permissionToSensitivesMap.entries().stream()
                .collect(MyCollectors.toMultimapForCollection(Map.Entry::getValue, Map.Entry::getKey));
        sceneFieldSensMap = buildSceneFieldSensMap();
        permissionToFieldSensMap = buildPermissionToFieldSensMap();
        fieldSensToPermissionsMap = permissionToFieldSensMap.entries().stream()
                .collect(MyCollectors.toMultimapForCollection(Map.Entry::getValue, Map.Entry::getKey));
    }

    public List<SootMethod> getPermCheckers() {
        return HierarchyUtil.resolveAbstractDispatches(permCheckerDefs);
    }

    private ListMultimap<AndroidMethod, SootMethod> buildSceneMethodSensMap() {
        return HierarchyUtil.resolveAbstractDispatchesToMap(methodSensitiveDefs);
    }

    private SetMultimap<Set<String>, SootMethod> buildPermissionToSensitivesMap() {
        Map<Set<String>, List<AndroidMethod>> permissionToSensitiveDefMap = methodSensitiveDefs
                .stream().collect(Collectors.groupingBy(AndroidMethod::getPermissions));
        return permissionToSensitiveDefMap.keySet().stream()
                .collect(MyCollectors.toMultimap(
                        LinkedHashMultimap::create,
                        permSet -> permSet,
                        permSet -> permissionToSensitiveDefMap.get(permSet).stream()
                                .flatMap(androMeth -> sceneMethodSensMap.get(androMeth).stream())
                ));
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

    private ListMultimap<Set<String>, SootField> buildPermissionToFieldSensMap() {
        Map<Set<String>, List<FieldSensitiveDef>> permissionToFieldSensDefMap = fieldSensitiveDefs.stream()
                .collect(Collectors.groupingBy(FieldSensitiveDef::getPermissions));
        return permissionToFieldSensDefMap.keySet().stream()
                .collect(MyCollectors.toMultimap(
                        ArrayListMultimap::create,
                        permSet -> permSet,
                        permSet -> permissionToFieldSensDefMap.get(permSet).stream()
                                .map(sceneFieldSensMap::get)
                                .filter(Objects::nonNull)
                ));
    }

    public List<SootMethod> getSceneMethodSensitives() {
        return HierarchyUtil.resolveAbstractDispatches(methodSensitiveDefs, true);
    }

    /**
     * Return the permission set for this action string. Or return null if this action does not require permissions.
     */
    public Set<String> getPermissionsFor(String actionString) {
        //fixme temp demo implementation
        if (actionString.equals("android.intent.action.CALL")) {
            return Collections.singleton("android.permission.CALL_PHONE");
        } else {
            return Collections.emptySet();
        }
    }

    public List<SootMethod> getSceneParametricSensitives() {
        return HierarchyUtil.resolveAbstractDispatches(parametricSensDef, true);
    }

    public Collection<SootField> getSceneFieldSensitives() {
        return sceneFieldSensMap.values();
    }

    public Set<SootMethod> getMethodSensitivesFor(Set<String> permSet) {
        return permissionToSensitivesMap.get(permSet);
    }

    public List<SootField> getFieldSensitivesFor(Set<String> permSet) {
        return permissionToFieldSensMap.get(permSet);
    }

    /**
     * @return all sets of permissions for which there is a sensitive method definition.
     */
    public Set<Set<String>> getMethodPermissionSets() {
        return methodSensitiveDefs.stream().map(AndroidMethod::getPermissions).collect(Collectors.toSet());
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
        result.addAll(getFieldPermissionSets());
        return result;
    }

    public Set<String> getPermissionsFor(SootMethod meth) {
        return sensitivesToPermissionsMap.get(meth);
    }

    public Set<String> getPermissionsFor(SootField field) {
        return fieldSensToPermissionsMap.get(field);
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

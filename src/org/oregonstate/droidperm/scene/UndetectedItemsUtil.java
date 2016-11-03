package org.oregonstate.droidperm.scene;

import org.oregonstate.droidperm.util.PrintUtil;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.AbstractMultiMap;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/4/2016.
 */
public class UndetectedItemsUtil {

    /**
     * todo this will be getUndetectedFields
     */
    private static MultiMap<SootField, Stmt> getFieldUsages(
            Collection<SootField> sootFields, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootField, Stmt> undetected = SceneUtil.resolveFieldUsages(sootFields);
        MultiMap<SootField, Stmt> copy = new HashMultiMap<>(undetected);

        //filter out ignored
        for (SootField sensField : copy.keySet()) {
            copy.get(sensField).stream().filter(stmt -> outflowIgnoreSet.contains(SceneUtil.getMethodOf(stmt)))
                    .forEach(pair -> undetected.remove(sensField, pair));
        }

        //todo filter out detected
        return undetected;
    }

    /**
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public static Map<Set<String>, MultiMap<SootField, Stmt>> buildPermToUndetectedFieldSensMap(
            ScenePermissionDefService scenePermDef, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootField, Stmt> undetectedSens =
                getFieldUsages(scenePermDef.getSceneFieldSensitives(), outflowIgnoreSet);

        return scenePermDef.getFieldPermissionSets().stream().collect(Collectors.toMap(
                permSet -> permSet,
                permSet -> scenePermDef.getFieldSensitivesFor(permSet).stream().collect(
                        HashMultiMap::new,
                        (multiMap, field) -> multiMap.putAll(field, undetectedSens.get(field)),
                        AbstractMultiMap::putAll
                )
        ));
    }

    public static void printUndetectedFieldSensitives(ScenePermissionDefService scenePermDef,
                                                      Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, MultiMap<SootField, Stmt>> permToUndetectedFieldSensMap =
                buildPermToUndetectedFieldSensMap(scenePermDef, outflowIgnoreSet);
        printUndetectedSensitives(permToUndetectedFieldSensMap, "Undetected field sensitives");

        System.out.println("\nUndetected field sensitives execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    /**
     * todo detected should be a map from units to container methods (methods are only for debugging)
     *
     * @param outflowIgnoreSet - entries from this list are used to filter out call contexts. Works for
     *                         TwilightManager.
     */
    private static MultiMap<SootMethod, Stmt> getUndetectedCalls(
            List<SootMethod> sootMethods, Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootMethod, Stmt> undetected = SceneUtil.resolveMethodUsages(sootMethods);
        MultiMap<SootMethod, Stmt> copy = new HashMultiMap<>(undetected);

        //filter out ignored
        for (SootMethod sens : copy.keySet()) {
            copy.get(sens).stream().filter(stmt -> outflowIgnoreSet.contains(SceneUtil.getMethodOf(stmt)))
                    .forEach(pair -> undetected.remove(sens, pair));
        }

        //filter out detected
        detected.forEach(edge -> undetected.remove(edge.tgt(), edge.srcStmt()));
        return undetected;
    }

    public static void printUndetectedCheckers(ScenePermissionDefService scenePermDef,
                                               Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();
        MultiMap<SootMethod, Stmt> undetectedCheckers =
                getUndetectedCalls(scenePermDef.getPermCheckers(), detected, outflowIgnoreSet);

        System.out.println("\n\nUndetected checkers : " + undetectedCheckers.values().size() + "\n"
                + "========================================================================");
        for (SootMethod checker : undetectedCheckers.keySet()) {
            System.out.println(checker);
            for (Stmt stmt : undetectedCheckers.get(checker)) {
                System.out.println("\tfrom " + PrintUtil.toMethodLogString(stmt));
            }
        }
        System.out.println("\nUndetected checkers execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    public static void printUndetectedSensitives(ScenePermissionDefService scenePermDef,
                                                 Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, MultiMap<SootMethod, Stmt>> permToUndetectedSensMap =
                buildPermToUndetectedSensMap(scenePermDef, detected, outflowIgnoreSet);
        printUndetectedSensitives(permToUndetectedSensMap, "Undetected sensitives");

        System.out.println("\nUndetected sensitives execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    /**
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its callign context: method and stmt.
     */
    public static Map<Set<String>, MultiMap<SootMethod, Stmt>> buildPermToUndetectedSensMap(
            ScenePermissionDefService scenePermDef, Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootMethod, Stmt> undetectedSens =
                getUndetectedCalls(scenePermDef.getSceneMethodSensitives(), detected, outflowIgnoreSet);

        return scenePermDef.getMethodPermissionSets().stream().collect(Collectors.toMap(
                permSet -> permSet,
                permSet -> scenePermDef.getMethodSensitivesFor(permSet).stream().collect(
                        HashMultiMap::new,
                        (multiMap, meth) -> multiMap.putAll(meth, undetectedSens.get(meth)),
                        AbstractMultiMap::putAll
                )
        ));
    }

    /**
     * @param <T> Type representing a sensitive. Either SootMethod or SootField.
     */
    public static <T> void printUndetectedSensitives(Map<Set<String>, MultiMap<T, Stmt>> permToUndetectedSensMap,
                                                     final String header) {
        int count = permToUndetectedSensMap.values().stream().mapToInt(mMap -> mMap.values().size()).sum();

        System.out.println("\n\n" + header + " : " + count + "\n"
                + "========================================================================");
        for (Set<String> permSet : permToUndetectedSensMap.keySet()) {
            MultiMap<T, Stmt> currentSensMMap = permToUndetectedSensMap.get(permSet);
            if (currentSensMMap.isEmpty()) {
                continue;
            }
            System.out.println("\n" + permSet
                    + "\n------------------------------------------------------------------------");
            for (T sens : currentSensMMap.keySet()) {
                System.out.println(sens);
                for (Stmt stmt : currentSensMMap.get(sens)) {
                    System.out.println("\tfrom " + PrintUtil.toMethodLogString(stmt));
                }
            }
        }
    }
}

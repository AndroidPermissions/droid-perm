package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.perm.ScenePermissionDefService;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;
import soot.util.AbstractMultiMap;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/4/2016.
 */
public class UndetectedItemsUtil {

    /**
     * todo detected should be a map from units to container methods (methods are only for debugging)
     *
     * @param outflowIgnoreSet - entries from this list are used to filter out call contexts. Works for
     *                         TwilightManager.
     */
    private static MultiMap<SootMethod, Pair<Stmt, SootMethod>> getUndetectedCalls(
            List<SootMethod> sootMethods, Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> undetected = SceneUtil.resolveMethodUsages(sootMethods);
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> copy = new HashMultiMap<>(undetected);

        //filter out ignored
        for (SootMethod sens : copy.keySet()) {
            copy.get(sens).stream().filter(pair -> outflowIgnoreSet.contains(pair.getO2()))
                    .forEach(pair -> undetected.remove(sens, pair));
        }

        //filter out detected
        detected.forEach(
                edge -> undetected.remove(edge.tgt(), new Pair<>(edge.srcStmt(), edge.src())));
        return undetected;
    }

    public static void printUndetectedCheckers(ScenePermissionDefService scenePermDef,
                                               Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> undetectedCheckers =
                getUndetectedCalls(scenePermDef.getPermCheckers(), detected, outflowIgnoreSet);

        System.out.println("\n\nUndetected checkers : " + undetectedCheckers.values().size() + "\n"
                + "========================================================================");
        for (SootMethod checker : undetectedCheckers.keySet()) {
            System.out.println(checker);
            for (Pair<Stmt, SootMethod> call : undetectedCheckers.get(checker)) {
                System.out.println("\tfrom " + call.getO2() + " : " + call.getO1().getJavaSourceStartLineNumber());
            }
        }
        System.out.println("\nUndetected checkers execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    public static void printUndetectedSensitives(ScenePermissionDefService scenePermDef,
                                                 Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, MultiMap<SootMethod, Pair<Stmt, SootMethod>>> permToUndetectedSensMap =
                buildPermToUndetectedSensMap(scenePermDef, detected, outflowIgnoreSet);
        printUndetectedSensitives(permToUndetectedSensMap, "Undetected sensitives");

        System.out.println("\nUndetected sensitives execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    public static Map<Set<String>, MultiMap<SootMethod, Pair<Stmt, SootMethod>>> buildPermToUndetectedSensMap(
            ScenePermissionDefService scenePermDef, Set<Edge> detected, Set<SootMethod> outflowIgnoreSet) {
        MultiMap<SootMethod, Pair<Stmt, SootMethod>> undetectedSens =
                getUndetectedCalls(scenePermDef.getSceneMethodSensitives(), detected, outflowIgnoreSet);

        return scenePermDef.getPermissionSets().stream().collect(Collectors.toMap(
                permSet -> permSet,
                permSet -> scenePermDef.getPermissionToSensitivesMap().get(permSet).stream()
                        .collect(HashMultiMap::new,
                                (multiMap, meth) -> multiMap.putAll(meth, undetectedSens.get(meth)),
                                AbstractMultiMap::putAll
                        )
        ));
    }

    public static void printUndetectedSensitives(
            Map<Set<String>, MultiMap<SootMethod, Pair<Stmt, SootMethod>>> permToUndetectedSensMap,
            final String header) {
        int count = permToUndetectedSensMap.values().stream().mapToInt(mMap -> mMap.values().size()).sum();

        System.out.println("\n\n" + header + " : " + count + "\n"
                + "========================================================================");
        for (Set<String> permSet : permToUndetectedSensMap.keySet()) {
            MultiMap<SootMethod, Pair<Stmt, SootMethod>> currentSensMMap = permToUndetectedSensMap.get(permSet);
            if (currentSensMMap.isEmpty()) {
                continue;
            }
            System.out.println("\n" + permSet + "\n------------------------------------");
            for (SootMethod sens : currentSensMMap.keySet()) {
                System.out.println(sens);
                for (Pair<Stmt, SootMethod> call : currentSensMMap.get(sens)) {
                    System.out.println("\tfrom " + call.getO2() + " : " + call.getO1().getJavaSourceStartLineNumber());
                }
            }
        }
    }
}

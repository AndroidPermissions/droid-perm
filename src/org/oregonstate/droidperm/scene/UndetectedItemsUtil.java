package org.oregonstate.droidperm.scene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.oregonstate.droidperm.util.PrintUtil;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/4/2016.
 */
public class UndetectedItemsUtil {

    /**
     * todo this will be getUndetectedFields
     */
    private static Multimap<SootField, Stmt> getFieldUsages(
            Collection<SootField> sootFields, Predicate<SootMethod> classpathFilter) {
        Multimap<SootField, Stmt> undetected = SceneUtil.resolveFieldUsages(sootFields, classpathFilter);
        Multimap<SootField, Stmt> copy = HashMultimap.create(undetected);

        //filter out ignored
        for (SootField sensField : copy.keySet()) {
            copy.get(sensField).stream().filter(stmt -> !classpathFilter.test(SceneUtil.getMethodOf(stmt)))
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
    public static Map<Set<String>, Multimap<SootField, Stmt>> buildPermToUndetectedFieldSensMap(
            ScenePermissionDefService scenePermDef, Predicate<SootMethod> classpathFilter) {
        Multimap<SootField, Stmt> undetectedSens =
                getFieldUsages(scenePermDef.getSceneFieldSensitives(), classpathFilter);

        return scenePermDef.getFieldPermissionSets().stream().collect(Collectors.toMap(
                permSet -> permSet,
                permSet -> scenePermDef.getFieldSensitivesFor(permSet).stream().collect(
                        HashMultimap::create,
                        (Multimap, field) -> Multimap.putAll(field, undetectedSens.get(field)),
                        Multimap::putAll
                )
        ));
    }

    public static void printUndetectedFieldSensitives(ScenePermissionDefService scenePermDef,
                                                      Predicate<SootMethod> classpathFilter) {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, Multimap<SootField, Stmt>> permToUndetectedFieldSensMap =
                buildPermToUndetectedFieldSensMap(scenePermDef, classpathFilter);
        printUndetectedSensitives(permToUndetectedFieldSensMap, "Undetected field sensitives");

        System.out.println("\nUndetected field sensitives execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    /**
     * @param classpathFilter - only analyze entries accepted by this filter. Works for TwilightManager.
     */
    private static Multimap<SootMethod, Stmt> getUndetectedCalls(
            List<SootMethod> sootMethods, Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootMethod, Stmt> undetected = SceneUtil.resolveMethodUsages(sootMethods, classpathFilter);
        Multimap<SootMethod, Stmt> copy = HashMultimap.create(undetected);

        //filter out ignored
        for (SootMethod sens : copy.keySet()) {
            copy.get(sens).stream().filter(stmt -> !classpathFilter.test(SceneUtil.getMethodOf(stmt)))
                    .forEach(pair -> undetected.remove(sens, pair));
        }

        //filter out detected
        detected.forEach(edge -> undetected.remove(edge.tgt(), edge.srcStmt()));
        return undetected;
    }

    public static void printUndetectedCheckers(ScenePermissionDefService scenePermDef,
                                               Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        long startTime = System.currentTimeMillis();
        Multimap<SootMethod, Stmt> undetectedCheckers =
                getUndetectedCalls(scenePermDef.getPermCheckers(), detected, classpathFilter);

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
                                                 Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, Multimap<SootMethod, Stmt>> permToUndetectedSensMap =
                buildPermToUndetectedMethodSensMap(scenePermDef, detected, classpathFilter);
        printUndetectedSensitives(permToUndetectedSensMap, "Undetected sensitives");

        System.out.println("\nUndetected sensitives execution time: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    /**
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public static Map<Set<String>, Multimap<SootMethod, Stmt>> buildPermToUndetectedMethodSensMap(
            ScenePermissionDefService scenePermDef, Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootMethod, Stmt> undetectedSens =
                getUndetectedCalls(scenePermDef.getSceneMethodSensitives(), detected, classpathFilter);

        return scenePermDef.getMethodPermissionSets().stream().collect(Collectors.toMap(
                permSet -> permSet,
                permSet -> scenePermDef.getMethodSensitivesFor(permSet).stream().collect(
                        HashMultimap::create,
                        (Multimap, meth) -> Multimap.putAll(meth, undetectedSens.get(meth)),
                        Multimap::putAll
                )
        ));
    }

    /**
     * @param <T> Type representing a sensitive. Either SootMethod or SootField.
     */
    public static <T> void printUndetectedSensitives(Map<Set<String>, Multimap<T, Stmt>> permToUndetectedSensMap,
                                                     final String header) {
        int count = permToUndetectedSensMap.values().stream().mapToInt(mMap -> mMap.values().size()).sum();

        System.out.println("\n\n" + header + " : " + count + "\n"
                + "========================================================================");
        for (Set<String> permSet : permToUndetectedSensMap.keySet()) {
            Multimap<T, Stmt> currentSensMMap = permToUndetectedSensMap.get(permSet);
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

package org.oregonstate.droidperm.scene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.util.MyCollectors;
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

    private static Multimap<SootField, Stmt> getUndetectedFieldRefs(
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
    public static Map<Set<String>, SetMultimap<SootField, Stmt>> buildPermToUndetectedFieldSensMap(
            ScenePermissionDefService scenePermDef, Predicate<SootMethod> classpathFilter) {
        Multimap<SootField, Stmt> undetectedSens =
                getUndetectedFieldRefs(scenePermDef.getSceneFieldSensitives(), classpathFilter);

        return undetectedSens.keySet().stream().collect(Collectors.groupingBy(
                scenePermDef::getPermissionsFor,
                MyCollectors.toMultimapForCollection(field -> field, undetectedSens::get)
        ));
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

    /**
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public static Map<Set<String>, SetMultimap<SootMethod, Stmt>> buildPermToUndetectedMethodSensMap(
            ScenePermissionDefService scenePermDef, Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootMethod, Stmt> undetectedSens =
                getUndetectedCalls(scenePermDef.getSceneMethodSensitives(), detected, classpathFilter);
        return undetectedSens.keySet().stream().collect(Collectors.groupingBy(
                scenePermDef::getPermissionsFor,
                MyCollectors.toMultimapForCollection(meth -> meth, undetectedSens::get)
        ));
    }

    /**
     * @param <T>       Type representing a sensitive. Either SootMethod or SootField.
     * @param printStmt - if true, also print the statement that refers this sensitive.
     */
    public static <T> void printUndetectedSensitives(
            Map<Set<String>, SetMultimap<T, Stmt>> permToUndetectedSensMap,
            final String header, boolean printStmt) {
        int count = permToUndetectedSensMap.values().stream().mapToInt(mMap -> mMap.values().size()).sum();

        System.out.println("\n\n" + header + " : " + count + "\n"
                + "========================================================================");
        for (Set<String> permSet : permToUndetectedSensMap.keySet()) {
            SetMultimap<T, Stmt> currentSensMMap = permToUndetectedSensMap.get(permSet);
            System.out.println("\n" + permSet
                    + "\n------------------------------------------------------------------------");
            for (T sens : currentSensMMap.keySet()) {
                System.out.println(sens);
                for (Stmt stmt : currentSensMMap.get(sens)) {
                    System.out.println("\tfrom " + PrintUtil.toMethodLogString(stmt));
                    if (printStmt) {
                        System.out.println("\t\t" + stmt);
                    }
                }
            }
        }
    }

    public static List<PermissionDef> getPermDefsFor(
            Map<Set<String>, SetMultimap<SootMethod, Stmt>> permToReferredMethodSensMap,
            Map<Set<String>, SetMultimap<SootField, Stmt>> permToReferredFieldSensMap,
            ScenePermissionDefService scenePermDef) {
        return scenePermDef.getPermDefsFor(
                permToReferredMethodSensMap.values().stream().flatMap(mmap -> mmap.keySet().stream())
                        .collect(Collectors.toList()),
                permToReferredFieldSensMap.values().stream().flatMap(mmap -> mmap.keySet().stream())
                        .collect(Collectors.toList())
        );
    }
}

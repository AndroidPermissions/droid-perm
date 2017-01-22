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
            Collection<SootField> sootFields, Set<Stmt> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootField, Stmt> undetected = SceneUtil.resolveFieldUsages(sootFields, classpathFilter);
        Multimap<SootField, Stmt> copy = HashMultimap.create(undetected);

        //filter out ignored
        for (SootField sensField : copy.keySet()) {
            copy.get(sensField).stream().filter(stmt -> !classpathFilter.test(SceneUtil.getMethodOf(stmt)))
                    .forEach(pair -> undetected.remove(sensField, pair));
        }

        //filter out detected
        undetected.values().removeAll(detected);
        return undetected;
    }

    /**
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public static Map<Set<String>, SetMultimap<SootField, Stmt>> buildPermToUndetectedFieldSensMap(
            ScenePermissionDefService scenePermDef, Set<Stmt> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootField, Stmt> undetectedSens =
                getUndetectedFieldRefs(scenePermDef.getSceneFieldSensitives(), detected, classpathFilter);

        return undetectedSens.keySet().stream().collect(Collectors.groupingBy(
                scenePermDef::getPermissionsFor,
                MyCollectors.toMultimapForCollection(field -> field, undetectedSens::get)
        ));
    }

    /**
     * @param classpathFilter - only analyze entries accepted by this filter. Works for TwilightManager.
     */
    public static Multimap<SootMethod, Stmt> getUndetectedCalls(
            List<SootMethod> sootMethods, Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        Multimap<SootMethod, Stmt> undetected = SceneUtil.resolveMethodUsages(sootMethods, classpathFilter);
        filterOutIgnoredAndDetected(undetected, detected, classpathFilter);
        return undetected;
    }

    /**
     * @param classpathFilter - only analyze entries accepted by this filter. Works for TwilightManager.
     */
    public static Multimap<SootMethod, Stmt> getUndetectedCallsInCHA(
            List<SootMethod> sootMethods, Set<Edge> detected, Predicate<SootMethod> classpathFilter,
            SootMethod dummyMain) {
        Multimap<SootMethod, Stmt> undetected =
                SceneUtil.resolveMethodUsagesCHA(sootMethods, classpathFilter, dummyMain);
        filterOutIgnoredAndDetected(undetected, detected, classpathFilter);
        return undetected;
    }

    /**
     * From resolved statements, filter out what's in detected and what's not accepted by classpathFilter.
     */
    private static void filterOutIgnoredAndDetected(Multimap<SootMethod, Stmt> resolvedStatements, Set<Edge> detected,
                                                    Predicate<SootMethod> classpathFilter) {
        //filter out ignored
        resolvedStatements.entries().removeIf(entry -> !classpathFilter.test(SceneUtil.getMethodOf(entry.getValue())));

        //filter out detected
        detected.forEach(edge -> resolvedStatements.remove(edge.tgt(), edge.srcStmt()));
    }

    public static void printUndetectedCheckers(ScenePermissionDefService scenePermDef,
                                               Set<Edge> detected, Predicate<SootMethod> classpathFilter) {
        long startTime = System.currentTimeMillis();
        Multimap<SootMethod, Stmt> undetectedCheckers =
                getUndetectedCalls(scenePermDef.getPermCheckers(), detected, classpathFilter);

        PrintUtil.printMultimapOfStmtValues(undetectedCheckers, "Undetected checkers", "", "\t", "from ", false);

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
     * Map lvl 1: from permission sets to undetected sensitives having this set.
     * <p>
     * Map lvl2: from sensitive to its calling context: method and stmt.
     */
    public static Map<Set<String>, SetMultimap<SootMethod, Stmt>> buildPermToUndetectedMethodSensMapCHA(
            ScenePermissionDefService scenePermDef, Set<Edge> detected, Predicate<SootMethod> classpathFilter,
            SootMethod dummyMain) {
        Multimap<SootMethod, Stmt> undetectedSens =
                getUndetectedCallsInCHA(scenePermDef.getSceneMethodSensitives(), detected, classpathFilter, dummyMain);
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
                    PrintUtil.printStmt(stmt, "\t", "from ", printStmt);
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

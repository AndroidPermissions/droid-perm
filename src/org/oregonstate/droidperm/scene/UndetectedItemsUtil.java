package org.oregonstate.droidperm.scene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import soot.Scene;
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
     * From resolved statements, filter out what's in detected and what's not accepted by classpathFilter.
     */
    private static void filterOutIgnoredAndDetected(Multimap<?, Stmt> resolvedStatements,
                                                    Predicate<SootMethod> classpathFilter,
                                                    Collection<Stmt> stmtsToExclude) {
        //filter out ignored
        resolvedStatements.entries().removeIf(entry -> !classpathFilter.test(SceneUtil.getMethodOf(entry.getValue())));

        //filter out detected
        resolvedStatements.values().removeAll(stmtsToExclude);
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

    /**
     * Performs all scene and CHA-reachability analysis of method sensitives, field sensitives and checkers. If
     * dummyMain is null, CHA analysis will not be performed.
     */
    public static SceneAnalysisResult sceneAnalysis(Set<Edge> excludedSensEdges, Set<Stmt> excludedSensFieldRefs,
                                                    Set<Edge> excludedCheckEdges,
                                                    Set<Edge> excludedRequestEdges,
                                                    ScenePermissionDefService scenePermDef,
                                                    ClasspathFilter classpathFilter, SootMethod dummyMain) {
        SceneAnalysisResult sceneResult = new SceneAnalysisResult();

        List<Stmt> excludedSensStmts = excludedSensEdges.stream().map(Edge::srcStmt).collect(Collectors.toList());
        List<Stmt> excludedCheckerStmts = excludedCheckEdges.stream().map(Edge::srcStmt).collect(Collectors.toList());
        List<Stmt> excludedRequestStmts = excludedRequestEdges.stream().map(Edge::srcStmt).collect(Collectors.toList());

        Multimap<SootMethod, Stmt> undetectedMethodSens = HashMultimap.create();
        sceneResult.checkers = HashMultimap.create();
        sceneResult.requesters = HashMultimap.create();
        Multimap<SootField, Stmt> undetectedFieldSens = HashMultimap.create();

        SceneUtil.traverseClasses(Scene.v().getApplicationClasses(), classpathFilter,
                SceneUtil.createMethodUsagesCollector(scenePermDef.getSceneMethodSensitives(), undetectedMethodSens),
                SceneUtil.createMethodUsagesCollector(scenePermDef.getPermCheckers(), sceneResult.checkers),
                SceneUtil.createMethodUsagesCollector(scenePermDef.getPermRequesters(), sceneResult.requesters),
                SceneUtil.createFieldUsagesCollector(scenePermDef.getSceneFieldSensitives(), undetectedFieldSens));

        filterOutIgnoredAndDetected(undetectedMethodSens, classpathFilter, excludedSensStmts);
        filterOutIgnoredAndDetected(sceneResult.checkers, classpathFilter, excludedCheckerStmts);
        filterOutIgnoredAndDetected(sceneResult.requesters, classpathFilter, excludedRequestStmts);
        filterOutIgnoredAndDetected(undetectedFieldSens, classpathFilter, excludedSensFieldRefs);

        if (dummyMain != null) {
            Multimap<SootMethod, Stmt> undetectedMethodSensCHA = HashMultimap.create();
            sceneResult.checkersCHA = HashMultimap.create();
            sceneResult.requestersCHA = HashMultimap.create();
            Multimap<SootField, Stmt> undetectedFieldSensCHA = HashMultimap.create();

            SceneUtil.traverseCHACallGraph(dummyMain, classpathFilter,
                    SceneUtil.createMethodUsagesCollector(scenePermDef.getSceneMethodSensitives(),
                            undetectedMethodSensCHA),
                    SceneUtil.createMethodUsagesCollector(scenePermDef.getPermCheckers(), sceneResult.checkersCHA),
                    SceneUtil.createMethodUsagesCollector(scenePermDef.getPermRequesters(), sceneResult.requestersCHA),
                    SceneUtil.createFieldUsagesCollector(scenePermDef.getSceneFieldSensitives(),
                            undetectedFieldSensCHA));

            filterOutIgnoredAndDetected(undetectedMethodSensCHA, classpathFilter, excludedSensStmts);
            filterOutIgnoredAndDetected(sceneResult.checkersCHA, classpathFilter, excludedCheckerStmts);
            filterOutIgnoredAndDetected(sceneResult.requestersCHA, classpathFilter, excludedRequestStmts);
            filterOutIgnoredAndDetected(undetectedFieldSensCHA, classpathFilter, excludedSensFieldRefs);

            //Remove from regular scene results CHA-reachable results.
            undetectedMethodSens.values().removeAll(undetectedMethodSensCHA.values());
            sceneResult.checkers.values().removeAll(sceneResult.checkersCHA.values());
            sceneResult.requesters.values().removeAll(sceneResult.requestersCHA.values());
            undetectedFieldSens.values().removeAll(undetectedFieldSensCHA.values());

            sceneResult.permToReferredMethodSensMapCHA =
                    undetectedMethodSensCHA.keySet().stream().collect(Collectors.groupingBy(
                            scenePermDef::getPermissionsFor,
                            MyCollectors.toMultimapForCollection(meth -> meth, undetectedMethodSensCHA::get)
                    ));
            sceneResult.permToReferredFieldSensMapCHA =
                    undetectedFieldSensCHA.keySet().stream().collect(Collectors.groupingBy(
                            scenePermDef::getPermissionsFor,
                            MyCollectors.toMultimapForCollection(field -> field, undetectedFieldSensCHA::get)
                    ));

            sceneResult.permDefsCHA = UndetectedItemsUtil.getPermDefsFor(sceneResult.permToReferredMethodSensMapCHA,
                    sceneResult.permToReferredFieldSensMapCHA, scenePermDef);
        }

        //these have to be computed last because they may be modified by CHA items logic above
        sceneResult.permToReferredMethodSensMap =
                undetectedMethodSens.keySet().stream().collect(Collectors.groupingBy(
                        scenePermDef::getPermissionsFor,
                        MyCollectors.toMultimapForCollection(meth -> meth, undetectedMethodSens::get)
                ));
        sceneResult.permToReferredFieldSensMap =
                undetectedFieldSens.keySet().stream().collect(Collectors.groupingBy(
                        scenePermDef::getPermissionsFor,
                        MyCollectors.toMultimapForCollection(field -> field, undetectedFieldSens::get)
                ));
        sceneResult.permDefs = UndetectedItemsUtil.getPermDefsFor(sceneResult.permToReferredMethodSensMap,
                sceneResult.permToReferredFieldSensMap, scenePermDef);

        return sceneResult;
    }
}

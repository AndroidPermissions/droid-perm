package org.oregonstate.droidperm.sens;

import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;
import soot.util.MultiMap;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    public static void printHierarchySensitives(ScenePermissionDefService scenePermDef) throws IOException {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, MultiMap<SootMethod, Pair<Stmt, SootMethod>>> permToUndetectedMethodSensMap =
                UndetectedItemsUtil
                        .buildPermToUndetectedSensMap(scenePermDef, Collections.emptySet(), Collections.emptySet());
        UndetectedItemsUtil.printUndetectedSensitives(permToUndetectedMethodSensMap, "Collected method sensitives");

        Map<Set<String>, MultiMap<SootField, Pair<Stmt, SootMethod>>> permToUndetectedFieldSensMap =
                UndetectedItemsUtil
                        .buildPermToUndetectedFieldSensMap(scenePermDef, Collections.emptySet());
        UndetectedItemsUtil.printUndetectedFieldSensitives(permToUndetectedFieldSensMap, "Collected field sensitives");

        Set<Set<String>> coveredPermissionSets = Stream.concat(
                permToUndetectedMethodSensMap.keySet().stream()
                        .filter(permSet -> !permToUndetectedMethodSensMap.get(permSet).isEmpty()),
                permToUndetectedFieldSensMap.keySet().stream()
                        .filter(permSet -> !permToUndetectedFieldSensMap.get(permSet).isEmpty())
        ).collect(Collectors.toCollection(LinkedHashSet::new));
        printCoveredPermSet(coveredPermissionSets);

        System.out.println("\nTime to collect sensitives: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private static void printCoveredPermSet(Set<Set<String>> coveredPermissionSets) {
        System.out
                .println("\n\n" + "Permission sets covered by sensitives" + " : " + coveredPermissionSets.size() + "\n"
                        + "========================================================================");
        coveredPermissionSets.forEach(System.out::println);
    }
}

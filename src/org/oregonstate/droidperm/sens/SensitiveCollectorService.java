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
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    public static void printHierarchySensitives(ScenePermissionDefService scenePermDef) throws IOException {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, MultiMap<SootMethod, Pair<Stmt, SootMethod>>> permToUndetectedSensMap =
                UndetectedItemsUtil
                        .buildPermToUndetectedSensMap(scenePermDef, Collections.emptySet(), Collections.emptySet());
        UndetectedItemsUtil.printUndetectedSensitives(permToUndetectedSensMap, "Collected method sensitives");

        Map<Set<String>, MultiMap<SootField, Pair<Stmt, SootMethod>>> permToUndetectedFieldSensMap =
                UndetectedItemsUtil
                        .buildPermToUndetectedFieldSensMap(scenePermDef, Collections.emptySet());
        UndetectedItemsUtil.printUndetectedFieldSensitives(permToUndetectedFieldSensMap, "Collected field sensitives");

        System.out.println("\nTime to collect sensitives: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }
}

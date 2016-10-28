package org.oregonstate.droidperm.sens;

import org.oregonstate.droidperm.perm.ScenePermissionDefService;
import org.oregonstate.droidperm.util.UndetectedItemsUtil;
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
        UndetectedItemsUtil.printUndetectedSensitives(permToUndetectedSensMap, "Collected sensitives");

        System.out.println("\nTime to collect sensitives: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }
}

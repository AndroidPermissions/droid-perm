package org.oregonstate.droidperm.sens;

import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;
import soot.util.MultiMap;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    public static void hierarchySensitivesAnalysis(ScenePermissionDefService scenePermDef, File apkFile)
            throws Exception {
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
        printCollection(coveredPermissionSets, "Permission sets covered by sensitives");

        Set<String> declaredPermissions = getDeclaredPermissions(apkFile);
        printCollection(declaredPermissions, "Permissions declared in manifest");

        System.out.println("\nTime to collect sensitives: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private static Set<String> getDeclaredPermissions(File apkFile) throws IOException, XmlPullParserException {
        DPProcessManifest manifest = new DPProcessManifest(apkFile);
        return manifest.getDeclaredPermissions();
    }

    private static <T> void printCollection(Collection<T> collection, String header) {
        System.out.println("\n\n" + header + " : " + collection.size() + "\n"
                + "========================================================================");
        collection.forEach(System.out::println);
    }
}

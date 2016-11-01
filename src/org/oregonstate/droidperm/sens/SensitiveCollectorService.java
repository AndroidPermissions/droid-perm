package org.oregonstate.droidperm.sens;

import com.google.common.collect.Sets;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;
import soot.util.MultiMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    public static void hierarchySensitivesAnalysis(ScenePermissionDefService scenePermDef, File apkFile, File txtOut)
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

        Set<Set<String>> sensitivePermissionSets = Stream.concat(
                permToUndetectedMethodSensMap.keySet().stream()
                        .filter(permSet -> !permToUndetectedMethodSensMap.get(permSet).isEmpty()),
                permToUndetectedFieldSensMap.keySet().stream()
                        .filter(permSet -> !permToUndetectedFieldSensMap.get(permSet).isEmpty())
        ).collect(Collectors.toCollection(LinkedHashSet::new));
        printCollection(sensitivePermissionSets, "Permission sets required by sensitives");

        Set<String> declaredPermissions = getDeclaredPermissions(apkFile);
        printCollection(declaredPermissions, "Permissions declared in manifest");

        Set<Set<String>> undeclaredPermissionSets = sensitivePermissionSets.stream()
                .filter(permSet -> Collections.disjoint(permSet, declaredPermissions))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        printCollection(undeclaredPermissionSets, "Permissions sets used by sensitives but not declared in manifest");

        Set<String> usedPermissions = sensitivePermissionSets.stream().collect(MyCollectors.toFlatSet());
        Set<String> unusedPermissions = Sets.difference(declaredPermissions, usedPermissions);
        printCollection(unusedPermissions, "Permissions declared but not used by sensitives");
        if (txtOut != null) {
            printCollectionToFile(unusedPermissions, txtOut);
        }

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

    /**
     * Print the elements of a collection to file, one per line.
     */
    private static <T> void printCollectionToFile(Collection<T> collection, File file) throws IOException {
        List<String> itemsAsStrings =
                collection.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(file.toPath(), itemsAsStrings, Charset.defaultCharset());
    }
}

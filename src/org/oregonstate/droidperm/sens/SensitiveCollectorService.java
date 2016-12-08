package org.oregonstate.droidperm.sens;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.PermissionDefConverter;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.scene.ClasspathFilter;
import org.oregonstate.droidperm.scene.ScenePermissionDefService;
import org.oregonstate.droidperm.scene.SceneUtil;
import org.oregonstate.droidperm.scene.UndetectedItemsUtil;
import org.oregonstate.droidperm.util.MyCollectors;
import org.oregonstate.droidperm.util.PrintUtil;
import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    private static final Path DANGEROUS_PERM_FILE = Paths.get("config/DangerousPermissions.txt");
    private static Set<String> allDangerousPerm;
    public static final List<String> storagePerm =
            Arrays.asList("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE");

    public static void hierarchySensitivesAnalysis(ScenePermissionDefService scenePermDef,
                                                   ClasspathFilter classpathFilter, File apkFile, File xmlOut)
            throws Exception {
        long startTime = System.currentTimeMillis();

        Map<Set<String>, Multimap<SootMethod, Stmt>> permToReferredMethodSensMap = UndetectedItemsUtil
                .buildPermToUndetectedMethodSensMap(scenePermDef, Collections.emptySet(), classpathFilter);
        UndetectedItemsUtil.printUndetectedSensitives(permToReferredMethodSensMap, "Collected method sensitives");

        Map<Set<String>, Multimap<SootField, Stmt>> permToReferredFieldSensMap = UndetectedItemsUtil
                .buildPermToUndetectedFieldSensMap(scenePermDef, classpathFilter);
        UndetectedItemsUtil.printUndetectedSensitives(permToReferredFieldSensMap, "Collected field sensitives");

        Set<Set<String>> sensitivePermissionSets = Stream.concat(
                permToReferredMethodSensMap.keySet().stream()
                        .filter(permSet -> !permToReferredMethodSensMap.get(permSet).isEmpty()),
                permToReferredFieldSensMap.keySet().stream()
                        .filter(permSet -> !permToReferredFieldSensMap.get(permSet).isEmpty())
        ).collect(Collectors.toCollection(LinkedHashSet::new));
        PrintUtil.printCollection(sensitivePermissionSets, "Permission sets required by sensitives");

        Set<String> declaredPermissions = getDeclaredPermissions(apkFile);
        PrintUtil.printCollection(declaredPermissions, "Permissions declared in manifest");

        Set<String> referredPerm = SceneUtil.resolveConstantUsages(getAllDangerousPerm(), classpathFilter).keySet();
        PrintUtil.printCollection(referredPerm, "Permissions referred in the code");

        Set<Set<String>> undeclaredPermissionSets = sensitivePermissionSets.stream()
                .filter(permSet -> Collections.disjoint(permSet, declaredPermissions))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        PrintUtil.printCollection(undeclaredPermissionSets,
                "Permissions sets used by sensitives but not declared in manifest");

        Set<String> permissionsWithSensitives = sensitivePermissionSets.stream().collect(MyCollectors.toFlatSet());
        Set<String> dangerousPermWithSensitives = Sets.intersection(permissionsWithSensitives, getAllDangerousPerm());
        Set<String> unusedPermissions = Sets.difference(declaredPermissions, permissionsWithSensitives);
        PrintUtil.printCollection(unusedPermissions, "Permissions declared but not used by sensitives");

        Set<String> declaredDangerousPerm = Sets.intersection(declaredPermissions, getAllDangerousPerm());
        Set<String> unusedDangerousPerm = Sets.intersection(unusedPermissions, getAllDangerousPerm());
        PrintUtil.printCollection(declaredDangerousPerm, "Dangerous permissions declared in manifest");
        PrintUtil.printCollection(unusedDangerousPerm, "Dangerous permissions declared but not used by sensitives");

        if (xmlOut != null) {
            SensitiveCollectorJaxbData data = new SensitiveCollectorJaxbData(
                    new ArrayList<>(declaredPermissions),
                    new ArrayList<>(declaredDangerousPerm),
                    new ArrayList<>(referredPerm),
                    new ArrayList<>(dangerousPermWithSensitives),
                    buildXmlPermDefs(permToReferredMethodSensMap, permToReferredFieldSensMap, scenePermDef));
            JaxbUtil.save(data, SensitiveCollectorJaxbData.class, xmlOut);
        }

        System.out.println("\nTime to collect sensitives: "
                + (System.currentTimeMillis() - startTime) / 1E3 + " seconds");
    }

    private static List<PermissionDef> buildXmlPermDefs(
            Map<Set<String>, Multimap<SootMethod, Stmt>> permToReferredMethodSensMap,
            Map<Set<String>, Multimap<SootField, Stmt>> permToReferredFieldSensMap,
            ScenePermissionDefService scenePermDef) {
        return Stream.concat(
                permToReferredMethodSensMap.values().stream().flatMap(mmap -> mmap.keySet().stream())
                        .map(meth -> PermissionDefConverter.forMethod(scenePermDef.getPermDefFor(meth))),
                permToReferredFieldSensMap.values().stream().flatMap(mmap -> mmap.keySet().stream())
                        .map(field -> PermissionDefConverter.forField(scenePermDef.getPermDefFor(field)))
        ).collect(Collectors.toList());
    }

    private static Set<String> getDeclaredPermissions(File apkFile) throws IOException, XmlPullParserException {
        DPProcessManifest manifest = new DPProcessManifest(apkFile);
        return manifest.getDeclaredPermissions();
    }

    public static Set<String> getAllDangerousPerm() {
        if (allDangerousPerm == null) {
            try {
                allDangerousPerm = loadDangerousPermissions();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return allDangerousPerm;
    }

    private static Set<String> loadDangerousPermissions() throws IOException {
        return Files.readAllLines(DANGEROUS_PERM_FILE).stream()
                .filter(line -> !(line.trim().isEmpty() || line.startsWith("%")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

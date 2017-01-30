package org.oregonstate.droidperm.perm.axplorer;

import com.google.common.collect.Sets;
import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.main.DroidPermMain;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;
import org.oregonstate.droidperm.util.PrintUtil;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 1/29/2017.
 */
public class PermDiffTool {

    /**
     * Prints the difference between 2 permission definition sets. Each set may have multiple files.
     * Prints:
     * <p>
     * (a) The list of permission defs present in the first set but not in the second
     * <p>
     * (b) The list of permission defs for which permissions differ.
     * <p>
     * arg 1: source set. Files separated by ";"
     * <p>
     * arg 2: destination set. Files separated by ";"
     * <p>
     * arg 3: out xml file
     * <p>
     * arg 4: boolean. Keep method permissions only.
     */
    public static void main(String[] args) throws JAXBException, IOException {
        List<File> set1Files = DroidPermMain.buildPermDefFiles(args[0]);
        List<File> set2Files = DroidPermMain.buildPermDefFiles(args[1]);
        File outFile = new File(args[2]);
        boolean methodPermOnly = Boolean.parseBoolean(args[3]);

        LinkedHashSet<PermissionDef> permDefSet1 = loadPermDefs(set1Files, methodPermOnly);
        LinkedHashSet<PermissionDef> permDefSet2 = loadPermDefs(set2Files, methodPermOnly);
        Map<PermissionDef, PermissionDef> permDefMap2 = permDefSet2.stream().collect(Collectors.toMap(
                def -> def, def -> def
        ));

        Data data = new Data();
        data.permDefCommon = Sets.intersection(permDefSet2, permDefSet1);
        data.permDefSet1Only = Sets.difference(permDefSet1, permDefSet2);
        data.permDefSet2Only = Sets.difference(permDefSet2, permDefSet1);
        data.permDefDiffPermissionsV1 = data.permDefCommon.stream()
                .filter(permDef -> !permDef.getPermissionNames().equals(permDefMap2.get(permDef).getPermissionNames()))
                .collect(Collectors.toList());
        data.permDefDiffPermissionsV2 = data.permDefDiffPermissionsV1.stream().map(permDefMap2::get)
                .collect(Collectors.toList());

        PrintUtil.printCollection(data.permDefCommon, "Common permission defs");
        PrintUtil.printCollection(data.permDefSet1Only, "Permission defs in set 1 only");
        PrintUtil.printCollection(data.permDefSet2Only, "Permission defs in set 2 only");
        PrintUtil.printCollection(data.permDefDiffPermissionsV1,
                "Permission defs with different permission values, version from set 1");
        PrintUtil.printCollection(data.permDefDiffPermissionsV2,
                "Permission defs with different permission values, version from set 2");
        JaxbUtil.save(data, Data.class, outFile);
    }

    private static LinkedHashSet<PermissionDef> loadPermDefs(List<File> xmlFiles, boolean methodPermOnly) {
        LinkedHashSet<PermissionDef> result = xmlFiles.stream()
                .flatMap(file -> {
                    try {
                        return JaxbUtil.load(PermissionDefList.class, file).getPermissionDefs().stream();
                    } catch (JAXBException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toCollection(LinkedHashSet::new));
        if (methodPermOnly) {
            result.removeIf(permDef -> permDef.getTargetKind() != PermTargetKind.Method);
        }
        return result;
    }

    @XmlRootElement(name = "PermissionDiff")
    @XmlAccessorType(XmlAccessType.FIELD)
    private static class Data {
        @XmlElementWrapper(name = "permDefCommonList")
        @XmlElement(name = "permDefCommon")
        Set<PermissionDef> permDefCommon;
        @XmlElementWrapper(name = "permDefSet1OnlyList")
        @XmlElement(name = "permDefSet1Only")
        Set<PermissionDef> permDefSet1Only;
        @XmlElementWrapper(name = "permDefSet2OnlyList")
        @XmlElement(name = "permDefSet2Only")
        Set<PermissionDef> permDefSet2Only;
        @XmlElementWrapper(name = "permDefDiffPermissionsV1List")
        @XmlElement(name = "permDefDiffPermissionsV1")
        List<PermissionDef> permDefDiffPermissionsV1;
        @XmlElementWrapper(name = "permDefDiffPermissionsV2List")
        @XmlElement(name = "permDefDiffPermissionsV2")
        List<PermissionDef> permDefDiffPermissionsV2;
    }
}

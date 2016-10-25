package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import soot.jimple.infoflow.android.data.AndroidMethod;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
public class XMLPermissionDefParser {

    public static Set<AndroidMethod> buildSensitiveDefs(File xmlPermDefFile) throws JAXBException {
        return buildSensitiveDefs(XmlPermDefMiner.load(xmlPermDefFile).getPermissionDefs());
    }

    public static Set<AndroidMethod> buildSensitiveDefs(List<PermissionDef> permissionDefs) {
        Set<AndroidMethod> xmlSensitives = new LinkedHashSet<>();
        for (PermissionDef permissionDef : permissionDefs) {
            //todo permissions targeting fields are ignored for the moment
            if (permissionDef.getTargetKind() == PermTargetKind.Method) {
                AndroidMethod sensitiveDef = toAndroidMethod(permissionDef);
                xmlSensitives.add(sensitiveDef);
            }
        }
        return xmlSensitives;
    }

    private static AndroidMethod toAndroidMethod(PermissionDef permissionDef) {
        int firstSpace = permissionDef.getTarget().indexOf(' ');
        String returnType = permissionDef.getTarget().substring(0, firstSpace);
        String nameAndParams = permissionDef.getTarget().substring(firstSpace).trim();
        String[] nameThenParams = nameAndParams.split("[()]");
        String methodName = nameThenParams[0];

        //deleting all spaces here. If array length is 1, then param lsit is empty.
        String params = nameThenParams.length == 2 ? nameThenParams[1].replaceAll("\\s+", "") : "";
        List<String> paramList = Arrays.asList(params.split(","));

        Set<String> permissions = permissionDef.getPermissions().stream().map(Permission::getName)
                .collect(Collectors.toSet());

        return new AndroidMethod(methodName, paramList, returnType, permissionDef.getClassName(), permissions);
    }
}

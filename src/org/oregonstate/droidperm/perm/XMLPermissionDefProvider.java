package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.FieldSensitiveDef;
import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
public class XMLPermissionDefProvider implements IPermissionDefProvider {

    private final Set<AndroidMethod> methodSensitiveDefs;
    private final Set<FieldSensitiveDef> fieldSensitiveDefs;

    public XMLPermissionDefProvider(File xmlPermDefFile) throws JAXBException {
        this(XmlPermDefMiner.load(xmlPermDefFile).getPermissionDefs());
    }

    public XMLPermissionDefProvider(List<PermissionDef> permissionDefs) {
        methodSensitiveDefs = permissionDefs.stream()
                .filter(permissionDef -> permissionDef.getTargetKind() == PermTargetKind.Method)
                .map(XMLPermissionDefProvider::toAndroidMethod).collect(Collectors.toCollection(LinkedHashSet::new));
        fieldSensitiveDefs = permissionDefs.stream()
                .filter(permissionDef -> permissionDef.getTargetKind() == PermTargetKind.Field)
                .map(XMLPermissionDefProvider::toFieldSensitiveDef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static FieldSensitiveDef toFieldSensitiveDef(PermissionDef permissionDef) {
        return new FieldSensitiveDef(permissionDef.getClassName(), permissionDef.getTarget(),
                permissionDef.getPermissionNames());
    }

    private static AndroidMethod toAndroidMethod(PermissionDef permissionDef) {
        int firstSpace = permissionDef.getTarget().indexOf(' ');
        String returnType = permissionDef.getTarget().substring(0, firstSpace);
        String nameAndParams = permissionDef.getTarget().substring(firstSpace).trim();
        String[] nameThenParams = nameAndParams.split("[()]");
        String methodName = nameThenParams[0];

        //Deleting all spaces here. If array length is 1, then param list is empty.
        String params = nameThenParams.length == 2 ? nameThenParams[1].replaceAll("\\s+", "") : "";
        List<String> paramList = Arrays.asList(params.split(","));

        Set<String> permissions = permissionDef.getPermissionNames();
        return new AndroidMethod(methodName, paramList, returnType, permissionDef.getClassName(), permissions);
    }

    @Override
    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return Collections.emptySet();
    }

    @Override
    public Set<AndroidMethod> getMethodSensitiveDefs() {
        return methodSensitiveDefs;
    }

    @Override
    public Set<FieldSensitiveDef> getFieldSensitiveDefs() {
        return fieldSensitiveDefs;
    }
}

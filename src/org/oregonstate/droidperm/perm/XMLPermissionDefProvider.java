package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_out.*;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 7/28/2016.
 */
class XMLPermissionDefProvider implements IPermissionDefProvider {

    /**
     * Groups: 1 = return type, 2 = method name, 3 = parameters
     */
    private static final String methodSigRegex = "\\s*([^\\s]+)\\s+([^\\s]+)\\s*\\((.*)\\)";
    private static final Pattern methodSigPattern = Pattern.compile(methodSigRegex);

    private final Set<SootMethodAndClass> permCheckerDefs;
    private final Set<AndroidMethod> methodSensitiveDefs;
    private final Set<FieldSensitiveDef> fieldSensitiveDefs;
    private final Set<SootMethodAndClass> parametricSensDefs;

    public XMLPermissionDefProvider(File xmlPermDefFile) throws JAXBException {
        this(JaxbUtil.load(PermissionDefList.class, xmlPermDefFile));
    }

    public XMLPermissionDefProvider(PermissionDefList permDefList) {
        permCheckerDefs = permDefList.getCheckerDefs().stream()
                .map(XMLPermissionDefProvider::toSootMethodAndClass)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        methodSensitiveDefs = permDefList.getPermissionDefs().stream()
                .filter(permissionDef -> permissionDef.getTargetKind() == PermTargetKind.Method)
                .map(XMLPermissionDefProvider::toAndroidMethod).collect(Collectors.toCollection(LinkedHashSet::new));
        fieldSensitiveDefs = permDefList.getPermissionDefs().stream()
                .filter(permissionDef -> permissionDef.getTargetKind() == PermTargetKind.Field)
                .map(XMLPermissionDefProvider::toFieldSensitiveDef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        parametricSensDefs = permDefList.getParametricSensDefs().stream()
                .map(XMLPermissionDefProvider::toSootMethodAndClass)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static SootMethodAndClass toSootMethodAndClass(ParametricSensDef parametricSensDef) {
        return toSootMethodAndClass(new CheckerDef(parametricSensDef.getClassName(), parametricSensDef.getTarget()));
    }

    private static SootMethodAndClass toSootMethodAndClass(CheckerDef checkerDef) {
        Matcher matcher = methodSigPattern.matcher(checkerDef.getTarget());
        if (!matcher.find()) {
            throw new RuntimeException("Invalid signature for permission checker: " + checkerDef.getTarget());
        }
        String returnType = matcher.group(1);
        String methodName = matcher.group(2);
        String unsplitParameters = matcher.group(3);
        List<String> parameters =
                Stream.of(unsplitParameters.split(",")).map(String::trim).collect(Collectors.toList());
        return new SootMethodAndClass(methodName, checkerDef.getClassName(), returnType, parameters);
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
        return permCheckerDefs;
    }

    @Override
    public Set<AndroidMethod> getMethodSensitiveDefs() {
        return methodSensitiveDefs;
    }

    @Override
    public Set<FieldSensitiveDef> getFieldSensitiveDefs() {
        return fieldSensitiveDefs;
    }

    @Override
    public Set<SootMethodAndClass> getParametricSensDefs() {
        return parametricSensDefs;
    }
}

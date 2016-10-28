package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.jaxb_out.PermTargetKind;
import org.oregonstate.droidperm.perm.miner.jaxb_out.Permission;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionRel;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.tagkit.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/13/2016.
 */
class AnnoPermissionDefProvider implements IPermissionDefProvider {

    private static AnnoPermissionDefProvider instance;

    private final List<PermissionDef> permissionDefs;
    private final XMLPermissionDefProvider xmlPermissionDefProvider;

    protected static AnnoPermissionDefProvider getInstance() {
        if (instance == null) {
            instance = new AnnoPermissionDefProvider();
        }
        return instance;
    }

    public AnnoPermissionDefProvider() {
        permissionDefs = extractAnnotations();
        xmlPermissionDefProvider = new XMLPermissionDefProvider(permissionDefs);
    }

    private static List<PermissionDef> extractAnnotations() {
        List<PermissionDef> list = new ArrayList<>();

        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            for (SootMethod meth : sootClass.getMethods()) {
                PermissionDef permDef = getPermissionDef(meth, sootClass.getName(), meth.getSubSignature(),
                        PermTargetKind.Method);
                if (permDef != null) {
                    list.add(permDef);
                }
            }
            for (SootField field : sootClass.getFields()) {
                PermissionDef permDef =
                        getPermissionDef(field, sootClass.getName(), field.getName(), PermTargetKind.Field);
                if (permDef != null) {
                    list.add(permDef);
                }
            }
        }
        return list;
    }

    private static PermissionDef getPermissionDef(Host target, String className, String targetName,
                                                  PermTargetKind targetType) {
        AnnotationElem reqPermAnno = getAnnotationElem(target);
        if (reqPermAnno != null) {
            PermissionDef permDef;
            permDef = new PermissionDef();
            permDef.setClassName(className);
            permDef.setTarget(targetName);
            permDef.setTargetKind(targetType);
            if (reqPermAnno instanceof AnnotationStringElem) {
                AnnotationStringElem stringPermAnno = (AnnotationStringElem) reqPermAnno;
                permDef.addPermission(new Permission(stringPermAnno.getValue(), null));
            } else if (reqPermAnno instanceof AnnotationArrayElem) {
                AnnotationArrayElem arrayPermAnno = (AnnotationArrayElem) reqPermAnno;
                permDef.setPermissionRel(PermissionRel.fromAnnotName(arrayPermAnno.getName()));
                for (AnnotationElem annoElem : arrayPermAnno.getValues()) {
                    permDef.addPermission(new Permission(((AnnotationStringElem) annoElem).getValue(), null));
                }
            } else {
                throw new RuntimeException(
                        "Unknown annotation element type: " + reqPermAnno.getClass() + " for: " + reqPermAnno);
            }
            return permDef;
        } else {
            return null;
        }
    }

    private static AnnotationElem getAnnotationElem(Host host) {
        //Soot documentation says AnnotationTag should be used, but debug shows they have this tag instead.
        VisibilityAnnotationTag visAnnotationTag =
                (VisibilityAnnotationTag) host.getTag("VisibilityAnnotationTag");
        if (visAnnotationTag != null) {
            Collection<AnnotationElem> elems = visAnnotationTag.getAnnotations().stream()
                    .filter(anno -> anno.getType().equals("Landroid/support/annotation/RequiresPermission;"))
                    .flatMap(anno -> anno.getElems().stream())
                    .filter(elem -> !elem.getName().equals("conditional")) //anything else represents values
                    .collect(Collectors.toList());
            if (elems.size() == 1) {
                return elems.iterator().next();
            } else if (elems.size() > 1) {
                throw new RuntimeException(
                        "More than 1 RequiresPermission annotation per host " + host + " : " + elems);
            }
        }
        return null;
    }

    protected List<PermissionDef> getPermissionDefs() {
        return permissionDefs;
    }

    @Override
    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return xmlPermissionDefProvider.getPermCheckerDefs();
    }

    @Override
    public Set<AndroidMethod> getMethodSensitiveDefs() {
        return xmlPermissionDefProvider.getMethodSensitiveDefs();
    }

    @Override
    public Set<FieldSensitiveDef> getFieldSensitiveDefs() {
        return xmlPermissionDefProvider.getFieldSensitiveDefs();
    }
}

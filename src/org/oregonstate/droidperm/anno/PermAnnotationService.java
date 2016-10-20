package org.oregonstate.droidperm.anno;

import org.oregonstate.droidperm.perm.XMLPermissionDefParser;
import org.oregonstate.droidperm.perm.miner.jaxb_out.*;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.tagkit.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.util.*;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/13/2016.
 */
public class PermAnnotationService {

    private static List<PermissionDef> permissionDefs;

    public static List<PermissionDef> getPermissionDefs() {
        if (permissionDefs == null) {
            permissionDefs = Collections.unmodifiableList(extractAnnotations());
        }
        return permissionDefs;
    }

    private static List<PermissionDef> extractAnnotations() {
        List<PermissionDef> list = new ArrayList<>();

        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            for (SootMethod meth : sootClass.getMethods()) {
                PermissionDef permDef = getPermissionDef(meth, sootClass.getName(), meth.getSubSignature(),
                        TargetType.Method);
                if (permDef != null) {
                    list.add(permDef);
                }
            }
            for (SootField field : sootClass.getFields()) {
                PermissionDef permDef = getPermissionDef(field, sootClass.getName(), field.getName(), TargetType.Field);
                if (permDef != null) {
                    list.add(permDef);
                }
            }
        }
        return list;
    }

    private static PermissionDef getPermissionDef(Host target, String className, String targetName,
                                                  TargetType targetType) {
        AnnotationElem reqPermAnno = getAnnotationElem(target);
        if (reqPermAnno != null) {
            PermissionDef permDef;
            permDef = new PermissionDef();
            permDef.setClassName(className);
            permDef.setTargetName(targetName);
            permDef.setTargetType(targetType);
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
                    .map(AnnotationTag::getElems)
                    .findAny().orElse(null);
            if (elems != null) {
                if (elems.size() == 1) {
                    return elems.iterator().next();
                } else {
                    throw new RuntimeException(
                            "More than 1 RequiresPermission annotation per host " + host + " : " + elems);
                }
            }
        }
        return null;
    }

    public static void printAnnoPermDefs() {
        List<PermissionDef> permDefs = getPermissionDefs();
        System.out.println("\nRequiresPermission annotations: " + permDefs.size()
                + "\n========================================================================\n");

        PermissionDefList pdList = new PermissionDefList();
        pdList.setPermissionDefs(permDefs);

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(PermissionDefList.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // output pretty printed
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(pdList, System.out);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<AndroidMethod> getSensitiveDefs() {
        return XMLPermissionDefParser.buildXmlSensitives(getPermissionDefs());
    }
}

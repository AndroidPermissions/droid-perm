package org.oregonstate.droidperm.anno;

import org.oregonstate.droidperm.perm.XMLPermissionDefParser;
import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.*;
import soot.*;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.options.Options;
import soot.tagkit.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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

    public static void collectPermAnno(File apkFile, File xmlOut) throws JAXBException, IOException {
        String apkFilePath = apkFile.getAbsolutePath();

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
        Options.v().set_process_dir(Collections.singletonList(apkFilePath));
        Options.v().set_soot_classpath(apkFilePath);
        Options.v().set_process_multiple_dex(true);
        Main.v().autoSetOptions();
        Scene.v().loadNecessaryClasses();

        printAnnoPermDefs();
        if (xmlOut != null) {
            PermissionDefList out = new PermissionDefList();
            out.setPermissionDefs(getPermissionDefs());
            XmlPermDefMiner.save(out, xmlOut);
        }
    }
}

package org.oregonstate.droidperm.perm.miner;

import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbAnnotation;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItem;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItemList;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbVal;
import org.oregonstate.droidperm.perm.miner.jaxb_out.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
public class XmlPermDefMiner {

    private static final Logger logger = LoggerFactory.getLogger(XmlPermDefMiner.class);

    public static void minePermissionDefs(String metadataJar, File outputFile) throws JAXBException, IOException {
        List<JaxbItem> jaxbItems = loadMetadataXml(metadataJar);
        jaxbItems = filterItemList(jaxbItems);
        PermissionDefList permissionDefList = buildPermissionDefList(jaxbItems);

        JaxbUtil.save(permissionDefList, PermissionDefList.class, outputFile);
    }

    /**
     * This function takes a JaxbItemList and filters out all of the items that do not have the RequiresPermission
     * annotation
     *
     * @return a filtered JaxbItemList
     */
    private static List<JaxbItem> filterItemList(List<JaxbItem> unfilteredItems) throws JAXBException {
        return unfilteredItems.stream().filter(item ->
                item.getAnnotations().stream().anyMatch(anno -> anno.getName().contains("RequiresPermission"))
        ).collect(Collectors.toList());
    }

    private static PermissionDefList buildPermissionDefList(List<JaxbItem> jaxbItems) {
        PermissionDefList permissionDefList = new PermissionDefList();

        for (JaxbItem jaxbItem : jaxbItems) {
            PermissionDef permissionDef = new PermissionDef();
            String itemNameNoGenerics = scrubGenerics(jaxbItem.getName());

            //Methods: token 0 = class, 1 = return type, 2 = method name + ( + first arg,
            //  rest - other args. Last one with )
            //  Generics, if present at this stage, could mess up this rule because they might have spaces inside
            //Fields: token 0 = class, 1 = field name
            int firstSpace = itemNameNoGenerics.indexOf(' ');
            String className = itemNameNoGenerics.substring(0, firstSpace).trim();
            String rawSignature = itemNameNoGenerics.substring(firstSpace, itemNameNoGenerics.length()).trim();
            permissionDef.setClassName(processInnerClasses(className));
            permissionDef.setTarget(cleanupSignature(rawSignature));

            //Here we determine if the target of the permission is a method or a field
            permissionDef.setTargetKind(
                    permissionDef.getTarget().contains("(") && permissionDef.getTarget().contains(")")
                    ? PermTargetKind.Method : PermTargetKind.Field);

            //Finally iterate through the annotations, extract the relevant information and put it in a PermDef object
            populatePermissions(permissionDef, jaxbItem);

            //some permission defs wrongly have an empty set of permissions. They have to be elliminated here.
            if (!permissionDef.getPermissions().isEmpty()) {
                logger.warn("Empty permission set for: " + permissionDef);
                permissionDefList.addPermissionDef(permissionDef);
            }
        }
        return permissionDefList;
    }

    /**
     * Convert signature to the format expected by DroidPerm. Scrub generics, ... construct and convert inner class
     * names.
     */
    public static String cleanupSignature(String rawSignature) {
        String signature = scrubGenerics(rawSignature);
        signature = signature.replace("...", "[]");//process Java 5 "..." construct
        signature = processInnerClasses(signature);
        return signature;
    }

    /**
     * Replace "." with "$" when connecting inner class names.
     */
    public static String processInnerClasses(String str) {
        //Match a dot, followed by an uppercase java id including $, followed by a dot.
        //Last dot should be replaced by $
        String regex = "(\\.\\p{Upper}[\\w$]*)\\.";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            str = str.replaceAll(regex, "$1\\$");
            matcher = pattern.matcher(str);
        }
        return str;
    }

    /**
     * Does the bulk of the work for buildPermissionDefList. It gets the relevant information from a JaxbAnnotation
     * object and gives it to a PermissionDef object.
     */
    private static void populatePermissions(PermissionDef permissionDef, JaxbItem jaxbItem) {
        for (JaxbAnnotation jaxbAnnotation : jaxbItem.getAnnotations()) {
            List<Permission> permForThisAnno = new ArrayList<>();
            //This block handles the extra Read or Write tag that may be attached to a permission
            OperationKind opKind = jaxbAnnotation.getName().contains("Read") ? OperationKind.Read :
                                   jaxbAnnotation.getName().contains("Write") ? OperationKind.Write
                                                                              : null;
            for (JaxbVal jaxbVal : jaxbAnnotation.getVals()) {
                //When a value has the name 'apis' its value is not a permission, so we don't store it
                String valName = jaxbVal.getName();
                if ((valName.equals("apis"))) {
                    if (jaxbVal.getVal().contains("..22")) {
                        //Permissions in this annotation are for older android versions. Clearing.
                        permForThisAnno.clear();
                        break;
                    }
                } else {
                    String delims = "[{},\"]+";
                    String[] tokens = jaxbVal.getVal().split(delims);

                    for (String token : tokens) {
                        if (token.length() > 1) {

                            permForThisAnno.add(new Permission(token.trim(), opKind));
                        }
                    }

                    //Name could be either value, anyOf or allOf.
                    PermissionRel permRel = valName.equals("anyOf") ? PermissionRel.AnyOf
                                                                    : valName.equals("allOf")
                                                                      ? PermissionRel.AllOf : null;
                    permissionDef.setPermissionRel(permRel);
                }
            }
            permissionDef.getPermissions().addAll(permForThisAnno);
        }
    }

    /**
     * Removes generics constructs from the signature, e.g. anything between "<>" or multi-level "<>".
     * Works for any strigns: signatures, types or other.
     */
    private static String scrubGenerics(final String stringWithGenerics) {
        String result = stringWithGenerics;
        while (result.contains("<")) {
            String newResult = result.replaceAll("<[^<]*>", ""); // scrubbing generics one level at a time
            if (newResult.equals(result)) {
                throw new IllegalStateException("Illegal state reached while scrubbing generics: " + result);
            }
            result = newResult;
        }
        return result;
    }

    /**
     * Iterates through each of the annotations.xml files that are in the annotationFiles list and
     * unmarshalls the xml in each of the files thus turning them into JaxbItemList objects. It store these lists in a
     * List object, then iterates through this list of lists and combines each JaxbItem in every list into a single
     * combined JaxbItemList. This combined list can then be marshalled into a single xml file.
     *
     * @return JaxbItemList - a single object containing all of the items in the annotations files
     */
    public static List<JaxbItem> loadMetadataXml(String metadataJar) throws JAXBException, IOException {
        ZipFile zipFile = new ZipFile(metadataJar);
        List<JaxbItem> result = zipFile.stream().filter(entry -> entry.getName().contains("annotations.xml"))
                .map(entry -> {
                    try {
                        InputStream stream = zipFile.getInputStream(entry);
                        List<JaxbItem> jaxbItems = loadMetadataXml(stream);
                        stream.close();
                        return jaxbItems;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(Collection::stream).collect(Collectors.toList());
        zipFile.close();
        return result;
    }

    private static List<JaxbItem> loadMetadataXml(InputStream inputStream) throws JAXBException {
        Unmarshaller unmarshaller = JAXBContext.newInstance(JaxbItemList.class).createUnmarshaller();
        return ((JaxbItemList) unmarshaller.unmarshal(inputStream)).getItems();
    }
}

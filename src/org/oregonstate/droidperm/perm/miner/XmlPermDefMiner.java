package org.oregonstate.droidperm.perm.miner;

import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbAnnotation;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItem;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItemList;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbVal;
import org.oregonstate.droidperm.perm.miner.jaxb_out.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * This takes a JaxbItemList object and marshalls it into a .xml file
     *
     * @param data JaxbItemList to be marshalled
     * @param file file to marshall the data into
     */
    public static void saveMetadataXml(JaxbItemList data, File file) throws JAXBException {
        Marshaller jbMarshaller = JAXBContext.newInstance(JaxbItemList.class).createMarshaller();
        jbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jbMarshaller.marshal(data, file);
    }

    public static void save(PermissionDefList permissionDefList, File file) throws JAXBException, IOException {
        Marshaller marshaller = JAXBContext.newInstance(PermissionDefList.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        Path path = file.toPath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        marshaller.marshal(permissionDefList, file);
    }

    public static PermissionDefList load(File file) throws JAXBException {
        Unmarshaller unmarshaller = JAXBContext.newInstance(PermissionDefList.class).createUnmarshaller();
        return (PermissionDefList) unmarshaller.unmarshal(file);
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

    static void extractPermissionDefs(String metadataJar, File outputFile) throws JAXBException, IOException {
        List<JaxbItem> jaxbItems = loadMetadataXml(metadataJar);
        jaxbItems = filterItemList(jaxbItems);
        PermissionDefList permissionDefList = buildPermissionDefList(jaxbItems);

        save(permissionDefList, outputFile);
    }

    private static PermissionDefList buildPermissionDefList(List<JaxbItem> jaxbItems) {
        String delimiters = "[ ]+";
        PermissionDefList permissionDefList = new PermissionDefList();

        for (JaxbItem jaxbItem : jaxbItems) {
            PermissionDef permissionDef = new PermissionDef();

            //Break up the string on spaces
            //The string before the first space is the class name, what comes after is the method or field value
            String[] tokens = jaxbItem.getName().split(delimiters);

            String javaClassName = tokens[0];
            permissionDef.setClassName(processInnerClasses(javaClassName));

            //Here the method or field is rebuilt because it was likely broken by the previous split
            StringBuilder signatureBuilder = new StringBuilder();
            for (int i = 1; i < tokens.length; i++) {
                signatureBuilder.append(tokens[i]).append(" ");
            }

            String rawSignature = signatureBuilder.toString().trim();
            String signature = cleanupSignature(rawSignature);
            permissionDef.setTarget(signature);

            //Here we determine if the target of the permission is a method or a field
            permissionDef.setTargetKind(
                    permissionDef.getTarget().contains("(") && permissionDef.getTarget().contains(")")
                    ? PermTargetKind.Method : PermTargetKind.Field);

            //Finally iterate through the annotations, extract the relevant information and put it in a PermDef object
            extractPermissions(permissionDef, jaxbItem);

            //some permission defs wrongly have an empty set of permissions. They have to be elliminated here.
            if (!permissionDef.getPermissions().isEmpty()) {
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
    private static void extractPermissions(PermissionDef permissionDef, JaxbItem jaxbItem) {
        for (JaxbAnnotation jaxbAnnotation : jaxbItem.getAnnotations()) {
            //noinspection Convert2streamapi
            for (JaxbVal jaxbVal : jaxbAnnotation.getVals()) {
                //When a value has the name 'apis' its value is not a permission, so we don't store it
                if (!(jaxbVal.getName().contains("apis"))) {
                    String delims = "[{},\"]+";
                    String[] tokens = jaxbVal.getVal().split(delims);

                    for (String token : tokens) {
                        Permission permission = new Permission();
                        if (token.length() > 1) {
                            permission.setName(token.trim());

                            //This block handles the extra Read or Write tag that may be attached to a permission
                            if (jaxbAnnotation.getName().contains("Read")) {
                                permission.setOperationKind(OperationKind.Read);
                            }
                            if (jaxbAnnotation.getName().contains("Write")) {
                                permission.setOperationKind(OperationKind.Write);
                            }

                            permissionDef.addPermission(permission);
                        }
                    }

                    //This block handles the OR/AND relationship that may exist when an item has multiple permissions
                    //If there's no rel defined, it's AllOf.
                    PermissionRel permRel =
                            jaxbVal.getName().contains("anyOf") ? PermissionRel.AnyOf : PermissionRel.AllOf;
                    permissionDef.setPermissionRel(permRel);
                }
            }
        }
    }

    /**
     * Removes generics constructs from the signature, e.g. anything between "<>"
     */
    private static String scrubGenerics(String rawSignature) {
        if (rawSignature.contains("<")) {
            //Java generics are in greater than/less than brackets so we breakup the string on them
            String delims = "[<>]+";
            String[] token = rawSignature.split(delims);

            //When the string is broken every other token is java generics info so we skip these when rebuilding the string
            StringBuilder resultBuilder = new StringBuilder();
            for (int i = 0; i < token.length; i += 2) {
                //This check adds a space between the return type and the method signature. Questionable.
                String processedToken = i == 0 && !token[i].contains(" ") ? token[i] + " " : token[i];
                resultBuilder.append(processedToken);
            }
            return resultBuilder.toString();
        }
        return rawSignature;
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

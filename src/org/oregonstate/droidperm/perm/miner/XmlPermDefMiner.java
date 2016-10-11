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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
public class XmlPermDefMiner {

    /**
     * This function takes a .xml and turns it into  list of java objects
     *
     * @param file .xml file to be turned into objects
     * @return A JaxbItemList object
     * @throws JAXBException
     */
    public static JaxbItemList unmarshallXML(File file) throws JAXBException {

        JAXBContext jbContext = JAXBContext.newInstance(JaxbItemList.class);
        Unmarshaller unmarshaller = jbContext.createUnmarshaller();

        return (JaxbItemList) unmarshaller.unmarshal(file);
    }

    public static JaxbItemList unmarshallXMLFromIO(InputStream inputStream) throws JAXBException {

        JAXBContext jbContext = JAXBContext.newInstance(JaxbItemList.class);
        Unmarshaller unmarshaller = jbContext.createUnmarshaller();

        return (JaxbItemList) unmarshaller.unmarshal(inputStream);
    }

    /**
     * This takes a JaxbItemList object and marshalls it into a .xml file
     *
     * @param data JaxbItemList to be marshalled
     * @param file file to marshall the data into
     * @throws JAXBException
     */
    public static void marshallXML(JaxbItemList data, File file) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JaxbItemList.class);
        Marshaller jbMarshaller = jaxbContext.createMarshaller();

        jbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jbMarshaller.marshal(data, file);
    }

    /**
     * This functions marshalls a PermissionDefList object into xml
     *
     * @throws JAXBException
     */
    public static void marshallPermDef(PermissionDefList permissionDefList, File file) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(PermissionDefList.class);
        Marshaller marshaller = jaxbContext.createMarshaller();

        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        marshaller.marshal(permissionDefList, file);
    }

    public static PermissionDefList unmarshallPermDefs(File file) throws JAXBException {
        JAXBContext jbContext = JAXBContext.newInstance(PermissionDefList.class);
        Unmarshaller unmarshaller = jbContext.createUnmarshaller();
        return (PermissionDefList) unmarshaller.unmarshal(file);
    }

    /**
     * This function takes a JaxbItemList and filters out all of the items that do not have the RequiresPermission
     * annotation
     *
     * @return a filtered JaxbItemList
     * @throws JAXBException
     */
    public static JaxbItemList filterItemList(JaxbItemList unfilteredItems) throws JAXBException {
        JaxbItemList filteredItems = new JaxbItemList();
        JaxbItem jaxbItem;

        Iterator<JaxbItem> itemIterator = unfilteredItems.getItem().iterator();
        Iterator<JaxbAnnotation> annotationIterator;

        //A nested loop to read each item and each of its annotations
        while (itemIterator.hasNext()) {
            jaxbItem = itemIterator.next();
            annotationIterator = jaxbItem.getAnnotations().iterator();
            while (annotationIterator.hasNext()) {
                //This if does the actual filtering out of items that do do not use permissions
                if (annotationIterator.next().getName().contains("RequiresPermission")) {
                    filteredItems.addItem(jaxbItem);
                }
            }
        }
        return filteredItems;
    }

    static void extractPermissionDefs(String androidAnnotationsLocation, File saveFile)
            throws JAXBException, IOException {
        XmlPermDefMiner m = new XmlPermDefMiner();
        JaxbItemList jaxbItemList;
        PermissionDefList permissionDefList;
        jaxbItemList = m.combineItems(androidAnnotationsLocation);
        jaxbItemList = filterItemList(jaxbItemList);
        permissionDefList = m.ItemsToPermissionDefs(jaxbItemList);

        marshallPermDef(permissionDefList, saveFile);
    }

    /**
     * This functions iterates through each of the annotations.xml files that are in the annotationFiles list and
     * unmarshalls the xml in each of the files thus turning them into JaxbItemList objects. It store these lists in a
     * List object, then iterates through this list of lists and combines each JaxbItem in every list into a single
     * combined JaxbItemList. This combined list can then be marshalled into a single xml file.
     *
     * @return JaxbItemList - a single object containing all of the items in the annotations files
     * @throws JAXBException
     */
    public JaxbItemList combineItems(String androidAnnotationsLocation) throws JAXBException, IOException {
        JaxbItemList combinedItems = new JaxbItemList();

        List<JaxbItemList> jaxbItemLists = getXMLFromIOStream(androidAnnotationsLocation);

        Iterator<JaxbItemList> jaxbItemListIterator = jaxbItemLists.iterator();
        Iterator<JaxbItem> jaxbItemIterator;

        while (jaxbItemListIterator.hasNext()) {
            jaxbItemIterator = jaxbItemListIterator.next().getItem().iterator();
            while (jaxbItemIterator.hasNext()) {
                combinedItems.addItem(jaxbItemIterator.next());
            }
        }
        return combinedItems;
    }

    /**
     * This function converts JaxbItems into PermissionDefs. It calls a coule helper functions to extract information
     * from the JaxbItems.
     *
     * @return A PermissionDefList object
     */
    public PermissionDefList ItemsToPermissionDefs(JaxbItemList items) {
        String delimiters = "[ ]+";
        PermissionDefList permissionDefList = new PermissionDefList();

        for (JaxbItem jaxbItem : items.getItem()) {
            PermissionDef permissionDef = new PermissionDef();

            //Break up the string on spaces
            String[] tokens = jaxbItem.getName().split(delimiters);

            //The string before the first space is the class name, what comes after is the method or field value
            permissionDef.setClassName(tokens[0]);

            //Here the method or field is rebuilt because it was likely broken by the previous split
            StringBuilder firstBuilder = new StringBuilder();
            for (int i = 1; i < tokens.length; i++) {
                firstBuilder.append(tokens[i]).append(" ");
            }

            //This check ensure that any Java generics information is removed from the string
            if (firstBuilder.toString().contains("<")) {
                scrubJavaGenerics(permissionDef, firstBuilder);
            } else {
                permissionDef.setTargetName(firstBuilder.toString().trim());
            }

            //Here we determine if the target of the permission is a method or a field
            if (permissionDef.getTargetName().contains("(")
                    && permissionDef.getTargetName().contains(")")) {
                permissionDef.setTargetType(TargetType.Method);
            } else {
                permissionDef.setTargetType(TargetType.Field);
            }

            //Finally iterate through the annotations, extract the relevant information and put it in a PermDef object
            Iterator<JaxbAnnotation> jaxbAnnotationIterator = jaxbItem.getAnnotations().iterator();
            extractPermissions(permissionDef, jaxbAnnotationIterator);

            permissionDefList.addPermissionDef(permissionDef);
        }
        return permissionDefList;
    }

    /**
     * Does the bulk of the work for ItemsToPermissionDefs. It gets the relevant information from a JaxbAnnotation
     * object and gives it to a PermissionDef object.
     */
    private void extractPermissions(PermissionDef permissionDef, Iterator<JaxbAnnotation> jaxbAnnotationIterator) {
        while (jaxbAnnotationIterator.hasNext()) {
            JaxbAnnotation jaxbAnnotation = jaxbAnnotationIterator.next();

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
                                permission.setOperationType(OperationType.Read);
                            }
                            if (jaxbAnnotation.getName().contains("Write")) {
                                permission.setOperationType(OperationType.Write);
                            }

                            permissionDef.addPermission(permission);
                        }
                    }

                    //This block handles the OR/AND relationship that may exist when an item has multiple permissions
                    if (jaxbVal.getName().contains("anyOf")) {
                        permissionDef.setPermissionRel(PermissionRel.AnyOf);
                    } else {
                        permissionDef.setPermissionRel(PermissionRel.AllOf);
                    }
                }
            }
        }
    }

    /**
     * This function removes any java generics information from the methods that require permissions. It is a helper the
     * ItemsToPermissionDef function
     */
    private void scrubJavaGenerics(PermissionDef permissionDef, StringBuilder firstBuilder) {

        //Java generics are in greater than/less than brackets so we breakup the string on them
        String delims = "[<>]+";
        String[] token = firstBuilder.toString().split(delims);

        //When the string is broken every other token is java generics info so we skip these when rebuilding the string
        StringBuilder secondBuilder = new StringBuilder();
        for (int i = 0; i < token.length; i += 2) {
            //This check adds a space between the return type and the method signature
            if (i == 0) {
                secondBuilder.append(token[i]).append(" ");
            } else {
                secondBuilder.append(token[i]);
            }
        }
        permissionDef.setTargetName(secondBuilder.toString().trim());
    }

    public List<JaxbItemList> getXMLFromIOStream(String pathToJar) throws IOException, JAXBException {
        ZipFile zipFile = new ZipFile(pathToJar);
        List<JaxbItemList> jaxbItemLists = new ArrayList<>();

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().contains("annotations.xml")) {
                InputStream stream = zipFile.getInputStream(entry);
                jaxbItemLists.add(unmarshallXMLFromIO(stream));
                stream.close();
            }
        }
        zipFile.close();
        return jaxbItemLists;
    }
}

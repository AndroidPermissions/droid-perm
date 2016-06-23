package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
public class miningPermDef {

    private List<File> annotationFiles = new ArrayList<>();
    private JaxbItemList combinedItems = new JaxbItemList();
    private PermissionDefList permissionDefList = new PermissionDefList();

    public List<File> getAnnotationFiles() { return annotationFiles; }
    public JaxbItemList getCombinedItems() { return combinedItems; }
    public PermissionDefList getPermissionDefList() { return permissionDefList; }

    public void setCombinedItems(JaxbItemList combinedItems) {
        this.combinedItems = combinedItems;
    }

    /**
     * This function takes a .xml and turns it into  list of java objects
     * @param file .xml file to be turned into objects
     * @return A JaxbItemList object
     * @throws JAXBException
     */
    protected static JaxbItemList unmarshallXML (File file) throws JAXBException {

        JAXBContext jbContext  = JAXBContext.newInstance(JaxbItemList.class);
        Unmarshaller unmarshaller = jbContext.createUnmarshaller();

        return (JaxbItemList)unmarshaller.unmarshal(file);
    }

    /**
     * This takes a JaxbItemList object and marshalls it into a .xml file
     * @param data JaxbItemList to be marshalled
     * @param file file to marshall the data into
     * @throws JAXBException
     */
    protected static void marshallXML(JaxbItemList data, File file) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(JaxbItemList.class);
        Marshaller jbMarshaller = jaxbContext.createMarshaller();

        jbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        jbMarshaller.marshal(data, file);
    }

    public static void marshallPermDef(PermissionDefList permissionDefList, File file) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(PermissionDefList.class);
        Marshaller marshaller = jaxbContext.createMarshaller();

        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        marshaller.marshal(permissionDefList, file);
    }

    /**
     * This function takes a JaxbItemList and filters out all of the items that
     * do not have the RequiresPermission annotation
     * @param unfilteredItems
     * @return a filtered JaxbItemList
     * @throws JAXBException
     */
    protected static JaxbItemList filterItemList(JaxbItemList unfilteredItems) throws JAXBException {
        JaxbItemList filteredItems = new JaxbItemList();
        JaxbItem jaxbItem;

        Iterator<JaxbItem> itemIterator = unfilteredItems.getItem().iterator();
        Iterator<JaxbAnnotation> annotationIterator;

        while (itemIterator.hasNext()) {
            jaxbItem = itemIterator.next();
            annotationIterator = jaxbItem.getAnnotations().iterator();
            while (annotationIterator.hasNext()) {
                if (annotationIterator.next().getName().contains("RequiresPermission")){
                    filteredItems.addItem(jaxbItem);
                }
            }
        }
        return filteredItems;
    }

    /**
     * Helper to look through all of the files in AndroidAnnotations directory
     * @param absolutePath path to the AndroidAnnotations directory
     */
    protected void iterateFiles(String absolutePath) {
        File[] files = new File(absolutePath).listFiles();
        showFiles(files);
    }

    /**
     * Recursively search for the annotations.xml files throughout the AndroidAnnotations directory
     * and add them to the the member variable annotationFiles which is a list of files.
     * @param files and array of files and directorys from the helper that calls this
     */
    private void showFiles(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                showFiles(file.listFiles());
            } else {
                if(file.getName().contains("annotations.xml")) {
                    annotationFiles.add(file);
                }
            }
        }
    }

    /**
     * This functions iterates through each of the annotations.xml files that are in the annotationFiles
     * list and unmarshalls the xml in each of the files thus turning them into JaxbItemList objects. It
     * store these lists in a List object, then iterates through this list of lists and combines each
     * JaxbItem in every list into a single combined JaxbItemList. This combined list can then be marshalled
     * into a single xml file.
     * @throws JAXBException
     */
    protected void combineItems(String androidAnnotationsLocation) throws JAXBException {
        List<JaxbItemList> jaxbItemLists = new ArrayList<>();

        iterateFiles(androidAnnotationsLocation);
        Iterator<File> fileIterator = annotationFiles.iterator();

        while (fileIterator.hasNext()){
            jaxbItemLists.add(unmarshallXML(fileIterator.next()));
        }

        Iterator<JaxbItemList> jaxbItemListIterator = jaxbItemLists.iterator();
        Iterator<JaxbItem> jaxbItemIterator;

        while (jaxbItemListIterator.hasNext()) {
            jaxbItemIterator = jaxbItemListIterator.next().getItem().iterator();
            while (jaxbItemIterator.hasNext()) {
                combinedItems.addItem(jaxbItemIterator.next());
            }
        }
    }

    /**
     * This function carries out the real work of mining the permission definitions. Should be the function
     * called from outside the class if trying to use this class.
     * @param androidAnnotationsPath path to the AndroidAnnotations file
     * @param saveFilePath path to whee the reulting .xml should be saved
     * @throws JAXBException
     */
    public void minePermDefs(String androidAnnotationsPath, String saveFilePath) throws JAXBException{
        File f = new File(saveFilePath);

        combineItems(androidAnnotationsPath);

        combinedItems = filterItemList(combinedItems);

        marshallXML(combinedItems, f);
    }

    public void ItemsToPermissionDefs (JaxbItemList items) {
        String delimiters = "[ ]+";

        Iterator<JaxbItem> jaxbItemIterator = items.getItem().iterator();

        while (jaxbItemIterator.hasNext()) {
            PermissionDef permissionDef = new PermissionDef();
            JaxbItem jaxbItem = jaxbItemIterator.next();

            String[] tokens = jaxbItem.getName().split(delimiters);

            permissionDef.getTarget().setClassName(tokens[0]);

            StringBuilder firstBuilder = new StringBuilder();
            for(int i = 1; i < tokens.length; i++) {
                firstBuilder.append(tokens[i] + " ");
            }

            if(firstBuilder.toString().contains("<")) {
                String delims = "[<>]+";
                String[] token = firstBuilder.toString().split(delims);

                StringBuilder secondBuilder = new StringBuilder();
                for(int i = 0; i < token.length; i+=2) {
                    if(i == 0) {
                        secondBuilder.append(token[i] + " ");
                    }
                    else {
                        secondBuilder.append(token[i]);
                    }
                }
                permissionDef.getTarget().setMethodFieldName(secondBuilder.toString());
            }
            else {
                permissionDef.getTarget().setMethodFieldName(firstBuilder.toString());
            }

            if(permissionDef.getTarget().getMethodFieldName().contains("(")
                    && permissionDef.getTarget().getMethodFieldName().contains(")")) {
                permissionDef.getTarget().setTargetType(TargetType.Method);
            }
            else {
                permissionDef.getTarget().setTargetType(TargetType.Field);
            }

            Iterator<JaxbAnnotation> jaxbAnnotationIterator = jaxbItem.getAnnotations().iterator();
            while (jaxbAnnotationIterator.hasNext()) {
                JaxbAnnotation jaxbAnnotation = jaxbAnnotationIterator.next();

                Iterator<JaxbVal> jaxbValIterator = jaxbAnnotation.getVals().iterator();
                while (jaxbValIterator.hasNext()) {
                    JaxbVal jaxbVal = jaxbValIterator.next();

                    if(!(jaxbVal.getName().contains("apis"))) {
                        permissionDef.getTarget().addPermission(jaxbVal.getVal());
                        if(jaxbVal.getName().contains("anyOf")) {
                            permissionDef.getTarget().setPermissionRelationship("anyOf");
                        }
                        else {
                            permissionDef.getTarget().setPermissionRelationship("allOf");
                        }
                    }
                }
            }

            permissionDefList.addPermissionDef(permissionDef);
        }
    }
}

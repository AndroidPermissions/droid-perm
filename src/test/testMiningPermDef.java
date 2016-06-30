package test;

import org.oregonstate.droidperm.miningPermDef.*;

import org.junit.Test;
import static org.junit.Assert.*;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
public class testMiningPermDef {

    @Test
    public void testUnmarshallXML() throws JAXBException {
        miningPermDef m = new miningPermDef();
        JaxbItemList jaxbItems = new JaxbItemList();
        File f = new File("src\\test\\annotations\\annotations.xml");
        File f1 = new File("src\\test\\annotations\\annotations2.xml");

        jaxbItems.setItem(m.unmarshallXML(f).getItem());

        m.marshallXML(jaxbItems, f1);
    }

    @Test
    public void testMarshallXML() throws JAXBException {
        miningPermDef m = new miningPermDef();
        JaxbItemList jaxbItemList = new JaxbItemList();
        File f = new File("src\\test\\annotations\\annotations1.xml");

        JaxbItem item = new JaxbItem();
        JaxbAnnotation annotation = new JaxbAnnotation();
        JaxbVal val = new JaxbVal();

        val.setName("Val name");
        val.setVal("val value");

        annotation.setName("annotation name");
        annotation.addVal(val);

        item.setName("item name");
        item.addAnnotation(annotation);

        jaxbItemList.addItem(item);

        m.marshallXML(jaxbItemList, f);
    }

    @Test
    public void testFilterItems() throws JAXBException {
        miningPermDef m = new miningPermDef();
        JaxbItemList jaxbItems = new JaxbItemList();
        JaxbItemList filteredJaxbItems = new JaxbItemList();
        JaxbItem jaxbItem = null;

        File f = new File("src\\test\\annotations\\annotations.xml");
        File f1 = new File("src\\test\\annotations\\annotations3.xml");

        jaxbItems.setItem(m.unmarshallXML(f).getItem());

        filteredJaxbItems.setItem(m.filterItemList(jaxbItems).getItem());

        Iterator<JaxbItem> itemIterator = filteredJaxbItems.getItem().iterator();
        Iterator<JaxbAnnotation> annotationIterator = null;

        while (itemIterator.hasNext()) {
            jaxbItem = itemIterator.next();
            annotationIterator = jaxbItem.getAnnotations().iterator();
            assertTrue(annotationIterator.next().getName().contains("RequiresPermission"));
        }

        m.marshallXML(filteredJaxbItems, f1);
    }

    @Test
    public void testCombineItems() throws JAXBException, IOException {
        miningPermDef m = new miningPermDef();
        File f = new File("src\\test\\annotations\\annotations4.xml");
        JaxbItemList jaxbItemList;

        jaxbItemList = m.combineItems("src\\test\\annotations\\androidAnnotations.jar");

        m.marshallXML(m.filterItemList(jaxbItemList), f);
    }

    @Test
    public void testItemToPermDef() throws JAXBException, IOException {
        miningPermDef m = new miningPermDef();
        PermissionDefList permissionDefList;
        JaxbItemList jaxbItemList;
        File f1 = new File("src\\test\\annotations\\annotations5.xml");

        jaxbItemList = m.combineItems("src\\test\\annotations\\androidAnnotations.jar");
        jaxbItemList = m.filterItemList(jaxbItemList);

        permissionDefList = m.ItemsToPermissionDefs(jaxbItemList);

        m.marshallPermDef(permissionDefList, f1);
    }
}

package edu.oregonstate.droidperm.perm.miner;

import org.junit.Test;
import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbAnnotation;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItem;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbItemList;
import org.oregonstate.droidperm.perm.miner.jaxb_in.JaxbVal;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static org.junit.Assert.assertTrue;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/14/2016.
 */
public class TestMiningPermDef {

    @Test
    public void testUnmarshallXML() throws JAXBException {
        XmlPermDefMiner m = new XmlPermDefMiner();
        JaxbItemList jaxbItems = new JaxbItemList();
        File f = new File("src\\test\\annotations\\annotations.xml");
        File f1 = new File("src\\test\\annotations\\annotations2.xml");

        jaxbItems.setItem(m.unmarshallXML(f).getItem());

        m.marshallXML(jaxbItems, f1);
    }

    @Test
    public void testMarshallXML() throws JAXBException {
        XmlPermDefMiner m = new XmlPermDefMiner();
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
        XmlPermDefMiner m = new XmlPermDefMiner();
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
        XmlPermDefMiner m = new XmlPermDefMiner();
        File f = new File("src\\test\\annotations\\annotations4.xml");
        JaxbItemList jaxbItemList;

        jaxbItemList = m.combineItems("src\\test\\annotations\\androidAnnotations.jar");

        m.marshallXML(m.filterItemList(jaxbItemList), f);
    }

    @Test
    public void testItemToPermDef() throws JAXBException, IOException {
        XmlPermDefMiner m = new XmlPermDefMiner();
        PermissionDefList permissionDefList;
        JaxbItemList jaxbItemList;
        File f1 = new File("src\\test\\annotations\\annotations5.xml");

        jaxbItemList = m.combineItems("src\\test\\annotations\\androidAnnotations.jar");
        jaxbItemList = m.filterItemList(jaxbItemList);

        permissionDefList = m.ItemsToPermissionDefs(jaxbItemList);

        m.marshallPermDef(permissionDefList, f1);
    }
}

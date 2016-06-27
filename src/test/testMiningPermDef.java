package test;

import org.oregonstate.droidperm.miningPermDef.*;

import org.junit.Test;
import static org.junit.Assert.*;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.Iterator;
import java.util.List;

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
    public void testIterateFiles() {
        miningPermDef m = new miningPermDef();

        m.iterateFiles("src\\test\\androidAnnotations");

        assertEquals(76, m.getAnnotationFiles().size());
    }

    @Test
    public void testCombineItems() throws JAXBException {
        miningPermDef m = new miningPermDef();
        File f = new File("src\\test\\annotations\\annotations4.xml");

        m.combineItems("src\\test\\androidAnnotations");

        m.marshallXML(m.filterItemList(m.getCombinedItems()), f);
    }

    @Test
    public void testMinePermDefs() throws JAXBException {
        miningPermDef m = new miningPermDef();

        m.minePermDefs("src\\test\\androidAnnotations",
                "src\\test\\annotations\\minedpermdefs.xml");
    }

    @Test
    public void testItemToPermDef() throws JAXBException {
        miningPermDef m = new miningPermDef();
        PermissionDefList permissionDefList = new PermissionDefList();
        File f1 = new File("src\\test\\annotations\\annotations5.xml");

        m.combineItems("src\\test\\androidAnnotations");
        m.setCombinedItems(m.filterItemList(m.getCombinedItems()));

        m.ItemsToPermissionDefs(m.getCombinedItems());

        m.marshallPermDef(m.getPermissionDefList(), f1);
    }
}

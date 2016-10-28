package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.perm.miner.XmlPermDefMiner;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.IOException;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/27/2016.
 */
public class AnnoPermissionDefUtil {
    public static void printAnnoPermDefs() {
        System.out.println(
                "\nRequiresPermission annotations: "
                        + AnnoPermissionDefProvider.getInstance().getPermissionDefs().size()
                        + "\n========================================================================\n");

        PermissionDefList pdList = new PermissionDefList();
        pdList.setPermissionDefs(AnnoPermissionDefProvider.getInstance().getPermissionDefs());

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

    public static void collectPermAnno(File xmlOut) throws JAXBException, IOException {
        printAnnoPermDefs();
        if (xmlOut != null) {
            PermissionDefList out = new PermissionDefList();
            out.setPermissionDefs(AnnoPermissionDefProvider.getInstance().getPermissionDefs());
            XmlPermDefMiner.save(out, xmlOut);
        }
    }
}

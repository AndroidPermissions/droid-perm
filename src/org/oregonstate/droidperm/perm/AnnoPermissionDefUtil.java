package org.oregonstate.droidperm.perm;

import org.oregonstate.droidperm.jaxb.JaxbUtil;
import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDefList;

import javax.xml.bind.JAXBException;
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
            JaxbUtil.print(pdList, PermissionDefList.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static void collectPermAnno(File xmlOut) throws JAXBException, IOException {
        printAnnoPermDefs();
        if (xmlOut != null) {
            PermissionDefList out = new PermissionDefList();
            out.setPermissionDefs(AnnoPermissionDefProvider.getInstance().getPermissionDefs());
            JaxbUtil.save(out, PermissionDefList.class, xmlOut);
        }
    }
}

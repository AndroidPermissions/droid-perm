package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.JAXBException;
import java.io.File;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/27/2016.
 */
public class Main {

    public static void main(final String[] args) throws JAXBException {
        miningPermDef m = new miningPermDef();
        File saveFile = new File(args[1]);
        JaxbItemList jaxbItemList;
        PermissionDefList permissionDefList;

        jaxbItemList = m.combineItems(args[0]);
        jaxbItemList = m.filterItemList(jaxbItemList);
        permissionDefList = m.ItemsToPermissionDefs(jaxbItemList);

        m.marshallPermDef(permissionDefList, saveFile);
    }
}

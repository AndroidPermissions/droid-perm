package org.oregonstate.droidperm.miningPermDef;

import soot.JavaClassProvider;

import javax.xml.bind.JAXBException;
import java.io.File;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/27/2016.
 */
public class Main {

    public static void main(final String[] args) throws JAXBException {
        miningPermDef m = new miningPermDef();
        File saveFile = new File(args[1]);

        m.combineItems(args[0]);
        m.setCombinedItems(m.filterItemList(m.getCombinedItems()));
        m.ItemsToPermissionDefs(m.getCombinedItems());

        m.marshallPermDef(m.getPermissionDefList(), saveFile);
    }
}

package org.oregonstate.droidperm.perm.miner;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/27/2016.
 */
public class PermMinerMain {

    public static void main(final String[] args) throws JAXBException, IOException {
        String androidAnnotationsLocation = args[0];
        File saveFile = new File(args[1], args[2]);
        XmlPermDefMiner.extractPermissionDefs(androidAnnotationsLocation, saveFile);
    }
}

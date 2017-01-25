package org.oregonstate.droidperm.perm.miner;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/27/2016.
 */
public class PermMinerMain {

    public static void main(final String[] args) throws JAXBException, IOException {
        String metadataJar = args[0];
        File outputFile = new File(args[1]);
        XmlPermDefMiner.minePermissionDefs(metadataJar, outputFile, true);
    }
}

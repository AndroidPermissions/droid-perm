package org.oregonstate.droidperm.miningPermDef;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;

/**
 * @author George Harder <harderg@oregonstate.edu> Created on 6/27/2016.
 */
public class Main {

    public static void main(final String[] args) throws JAXBException, IOException {
        String androidAnnotationsLocation = args[0];
        File saveFile = new File(args[1]);
        miningPermDef.extractPermissionDefs(androidAnnotationsLocation, saveFile);
    }
}

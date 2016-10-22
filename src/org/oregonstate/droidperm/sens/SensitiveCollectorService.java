package org.oregonstate.droidperm.sens;

import org.oregonstate.droidperm.util.UndetectedItemsUtil;
import soot.jimple.infoflow.android.data.AndroidMethod;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/21/2016.
 */
public class SensitiveCollectorService {

    public static void printHierarchySensitives(Set<AndroidMethod> sensitiveDefs) throws IOException {
        UndetectedItemsUtil.printUndetectedSensitives(sensitiveDefs, Collections.emptySet(), Collections.emptySet());
    }
}

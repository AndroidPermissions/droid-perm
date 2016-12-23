package org.oregonstate.droidperm.scene;

import soot.SootMethod;

import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter for soot classes. Accepts only those that have to be included in the analysis.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 11/4/2016.
 */
public class ClasspathFilter implements Predicate<SootMethod> {

    Set<SootMethod> ignoreSet;

    public ClasspathFilter(Set<SootMethod> ignoreSet) {
        this.ignoreSet = ignoreSet;
    }

    /**
     * true means class is valid, should not be excluded.
     */
    @Override
    public boolean test(SootMethod sootMethod) {
        //Important: classes whose implementation is fully modeled, like Thread or AsyncTask, should not be excluded.
        String className = sootMethod.getDeclaringClass().getName();
        //classes inside java.* and javax.* are not excluded
        return className.equals("android.os.AsyncTask") ||
                !(className.startsWith("android.")
                        || className.startsWith("sun.")
                        || className.startsWith("com.google.android.")
                        || className.startsWith("org.omg.")
                        || className.startsWith("org.w3c.dom.")
                        || ignoreSet.contains(sootMethod));
    }
}

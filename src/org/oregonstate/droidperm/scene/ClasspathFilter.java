package org.oregonstate.droidperm.scene;

import soot.SootMethod;
import soot.jimple.infoflow.util.SystemClassHandler;

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

    @Override
    public boolean test(SootMethod sootMethod) {
        return !SystemClassHandler.isClassInSystemPackage(sootMethod.getDeclaringClass().getName())
                && !ignoreSet.contains(sootMethod);
    }
}

package org.oregonstate.droidperm.util;

import soot.MethodOrMethodContext;

import java.util.Comparator;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/23/2016.
 */
public class SortUtil {
    public static Comparator<MethodOrMethodContext> getSootMethodPrettyPrintComparator() {
        return (meth1, meth2) -> {
            int classCompare = meth1.method().getDeclaringClass().getName()
                    .compareTo(meth2.method().getDeclaringClass().getName());
            return classCompare != 0 ? classCompare
                    : meth1.method().getJavaSourceStartLineNumber() - meth2.method().getJavaSourceStartLineNumber();
        };
    }
}

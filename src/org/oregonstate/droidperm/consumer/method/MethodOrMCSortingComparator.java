package org.oregonstate.droidperm.consumer.method;

import soot.MethodOrMethodContext;

import java.util.Comparator;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/15/2016.
 */
public class MethodOrMCSortingComparator implements Comparator<MethodOrMethodContext> {
    @Override
    public int compare(MethodOrMethodContext o1, MethodOrMethodContext o2) {
        return o1.method().toString().compareTo(o2.method().toString());
    }
}

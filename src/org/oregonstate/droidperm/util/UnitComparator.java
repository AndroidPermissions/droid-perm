package org.oregonstate.droidperm.util;

import soot.SootMethod;
import soot.Unit;

import java.util.Comparator;
import java.util.Map;

/**
 * Compare units by containing class then by line number. Useful to sort units in a consistent order.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/22/2016.
 */
public class UnitComparator implements Comparator<Unit> {
    Map<Unit, SootMethod> stmtToMethodMap = CallGraphUtil.getStmtToMethodMap();

    @Override
    public int compare(Unit u1, Unit u2) {
        int classDiff = stmtToMethodMap.get(u1).getDeclaringClass().getName()
                .compareTo(stmtToMethodMap.get(u2).getDeclaringClass().getName());
        return classDiff != 0 ? classDiff : u1.getJavaSourceStartLineNumber() - u2.getJavaSourceStartLineNumber();
    }
}

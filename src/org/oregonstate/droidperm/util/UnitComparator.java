package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.scene.SceneUtil;
import soot.Unit;

import java.util.Comparator;

/**
 * Compare units by containing class then by line number. Useful to sort units in a consistent order.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 6/22/2016.
 */
public class UnitComparator implements Comparator<Unit> {

    @Override
    public int compare(Unit u1, Unit u2) {
        int classDiff = SceneUtil.getMethodOf(u1).getDeclaringClass().getName()
                .compareTo(SceneUtil.getMethodOf(u2).getDeclaringClass().getName());
        return classDiff != 0 ? classDiff : u1.getJavaSourceStartLineNumber() - u2.getJavaSourceStartLineNumber();
    }
}

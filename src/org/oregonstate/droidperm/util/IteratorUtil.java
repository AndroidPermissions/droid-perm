package org.oregonstate.droidperm.util;

import java.util.Iterator;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 7/14/2016.
 */
public class IteratorUtil {

    /**
     * Source: http://www.lambdafaq.org/how-can-i-turn-an-iterator-into-an-iterable/
     */
    public static <T> Iterable<T> asIterable(Iterator<T> iterator) {
        return () -> iterator;
    }
}

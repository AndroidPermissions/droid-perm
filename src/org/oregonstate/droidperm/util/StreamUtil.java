package org.oregonstate.droidperm.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Misc methods to facilitate conversion of Iterable/Iterator objects to streams.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 2/20/2016.
 */
public class StreamUtil {

    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        return asStream(() -> iterator, false);
    }

    public static <T> Stream<T> asStream(Iterable<T> iterable) {
        return asStream(iterable, false);
    }

    public static <T> Stream<T> asStream(Iterable<T> iterable, boolean parallel) {
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    public static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }

    public static <T> Set<T> mutableUnion(Set<T> set1, Set<T> set2) {
        set1.addAll(set2);
        return set1;
    }

    public static <T> Set<T> newObjectUnion(Set<T> set1, Set<T> set2) {
        Set<T> result = new HashSet<>(set1);
        result.addAll(set2);
        return result;
    }

    public static <T, U> Map<T, U> mutableMapCombiner(Map<T, U> map1, Map<T, U> map2) {
        map1.putAll(map2);
        return map1;
    }
}

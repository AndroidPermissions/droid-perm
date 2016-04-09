package org.oregonstate.droidperm.util;

import java.util.Iterator;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Misc methods to facilitate conversion of Iterable/Iterator objects to streams.
 *
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 2/20/2016.
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
}

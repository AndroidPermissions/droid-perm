package org.oregonstate.droidperm.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/21/2016.
 */
public class MyCollectors {

    static final Set<Collector.Characteristics> CH_UNORDERED_ID
            = Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.UNORDERED,
            Collector.Characteristics.IDENTITY_FINISH));

    /**
     * Returns a {@code Collector} that accumulates a stream of sets into one set, by flattening the elements of the
     * stream.
     *
     * @param <T> the type of the input elements of the sets in the stream.
     * @return a {@code Collector} which collects all the sets inside the stream into a {@code Set}
     */
    public static <T>
    Collector<Collection<T>, ?, Set<T>> toFlatSet() {
        return new CollectorImpl<>(HashSet::new, Set::addAll,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Function.identity(),
                CH_UNORDERED_ID);
    }

    public static <T, U>
    Collector<Map<T, U>, ?, Map<T, U>> toFlatMap() {
        return new CollectorImpl<>(HashMap::new, Map::putAll,
                (left, right) -> {
                    left.putAll(right);
                    return left;
                },
                Function.identity(),
                CH_UNORDERED_ID);
    }

    /**
     * Collector that takes a stream of elements as input and produces a multimap, by mapping each element to
     *
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function from input stream elements to streams of value elements.
     * @param <T>         The type of input elements.
     * @param <K>         The type of multimap keys.
     * @param <V>         The type of multimap values (elements of the set corresponding to each key)
     * @return a {@code Collector} which collects elements into a {@code MultiMap} whose keys are the result of applying
     * a key mapping function to the input elements, and whose values (which are sets) are the result of applying a
     * value mapping function which produces a stream, and then collecting the stream elements into a set.
     */
    public static <T, K, V>
    Collector<T, ?, MultiMap<K, V>> toMultiMap(Function<? super T, ? extends K> keyMapper,
                                               Function<? super T, ? extends Stream<? extends V>> valueMapper) {
        return new CollectorImpl<>(
                HashMultiMap::new,
                (MultiMap<K, V> multiMap, T elem)
                        -> multiMap.putAll(keyMapper.apply(elem), valueMapper.apply(elem).collect(Collectors.toSet())),
                (left, right) -> {
                    left.putAll(right);
                    return left;
                },
                Function.identity(),
                CH_UNORDERED_ID);
    }

    /**
     * Collector that takes a stream of elements as input and produces a Guava Multimap, by mapping each element through
     *
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function to produce values.
     * @param <T>         The type of input elements.
     * @param <K>         The type of multimap keys.
     * @param <V>         The type of multimap values (elements of the set corresponding to each key)
     * @return a {@code Collector} which collects elements into a {@code MultiMap}, by performing a grouping by
     * operation on the given keys.
     */
    public static <T, K, V>
    Collector<T, ?, Multimap<K, V>> toMultimapGroupingBy(Function<? super T, ? extends K> keyMapper,
                                                         Function<? super T, ? extends V> valueMapper) {
        return new MultimapCollector<>(HashMultimap::create, keyMapper, valueMapper, Function.identity());
    }

    /**
     * Collector that takes a stream of elements as input and produces a Guava Multimap, by mapping each element through
     *
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function from input stream elements to streams of value elements.
     * @param <T>         The type of input elements.
     * @param <K>         The type of multimap keys.
     * @param <V>         The type of multimap values (elements of the set corresponding to each key)
     * @return a {@code Collector} which collects elements into a {@code MultiMap} whose keys are the result of applying
     * a key mapping function to the input elements, and whose values (which are sets) are the result of applying a
     * value mapping function which produces a stream, and then collecting the stream elements into a set.
     */
    public static <T, K, V>
    Collector<T, ?, Multimap<K, V>> toMultimap(Function<? super T, ? extends K> keyMapper,
                                               Function<? super T, ? extends Stream<? extends V>> valueMapper) {
        return toMultimapForCollection(keyMapper,
                (T input) -> IteratorUtil.asIterable(valueMapper.apply(input).iterator()));
    }

    /**
     * Collector that takes a stream of elements as input and produces a Guava Multimap, by mapping each element through
     *
     * @param supplier    multimap supplier
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function from input stream elements to a collection of value elements.
     * @param <T>         The type of input elements.
     * @param <K>         The type of multimap keys.
     * @param <V>         The type of multimap values (elements of the set corresponding to each key)
     * @return a {@code Collector} which collects elements into a {@code MultiMap} whose keys are the result of applying
     * a key mapping function to the input elements, and whose values (which are sets) are the result of applying a
     * value mapping function which produces a stream, and then collecting the stream elements into a set.
     */
    public static <T, K, V>
    Collector<T, ?, Multimap<K, V>> toMultimapForCollection(Supplier<Multimap<K, V>> supplier,
                                                            Function<? super T, ? extends K> keyMapper,
                                                            Function<? super T,
                                                                    ? extends Iterable<? extends V>> valueMapper) {
        return new CollectorImpl<>(
                supplier,
                (Multimap<K, V> multimap, T elem)
                        -> multimap.putAll(keyMapper.apply(elem), valueMapper.apply(elem)),
                (left, right) -> {
                    left.putAll(right);
                    return left;
                },
                Function.identity(),
                CH_UNORDERED_ID);
    }

    /**
     * Collector that takes a stream of elements as input and produces a Guava Multimap, by mapping each element through
     *
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function from input stream elements to a collection of value elements.
     * @param <T>         The type of input elements.
     * @param <K>         The type of multimap keys.
     * @param <V>         The type of multimap values (elements of the set corresponding to each key)
     * @return a {@code Collector} which collects elements into a {@code MultiMap} whose keys are the result of applying
     * a key mapping function to the input elements, and whose values (which are sets) are the result of applying a
     * value mapping function which produces a stream, and then collecting the stream elements into a set.
     */
    public static <T, K, V>
    Collector<T, ?, Multimap<K, V>> toMultimapForCollection(Function<? super T, ? extends K> keyMapper,
                                                            Function<? super T,
                                                                    ? extends Iterable<? extends V>> valueMapper) {
        return toMultimapForCollection(HashMultimap::create, keyMapper, valueMapper);
    }

    /**
     * Copied from java.util.stream.Collectors.
     *
     * @param <T> the type of elements to be collected
     * @param <A> the type of the intermediary data structure used to accumulate values (usually same as R)
     * @param <R> the type of the result
     * @see java.util.stream.Collectors.CollectorImpl
     */
    static class CollectorImpl<T, A, R> implements Collector<T, A, R> {
        private final Supplier<A> supplier;
        private final BiConsumer<A, T> accumulator;
        private final BinaryOperator<A> combiner;
        private final Function<A, R> finisher;
        private final Set<Characteristics> characteristics;

        CollectorImpl(Supplier<A> supplier,
                      BiConsumer<A, T> accumulator,
                      BinaryOperator<A> combiner,
                      Function<A, R> finisher,
                      Set<Characteristics> characteristics) {
            this.supplier = supplier;
            this.accumulator = accumulator;
            this.combiner = combiner;
            this.finisher = finisher;
            this.characteristics = characteristics;
        }

        @Override
        public BiConsumer<A, T> accumulator() {
            return accumulator;
        }

        @Override
        public Supplier<A> supplier() {
            return supplier;
        }

        @Override
        public BinaryOperator<A> combiner() {
            return combiner;
        }

        @Override
        public Function<A, R> finisher() {
            return finisher;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return characteristics;
        }
    }
}

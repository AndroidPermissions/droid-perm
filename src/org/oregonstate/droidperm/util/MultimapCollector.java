package org.oregonstate.droidperm.util;

import com.google.common.collect.Multimap;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A Stream collector that returns a Multimap.
 * <p>
 * Source: http://stackoverflow.com/a/30635537/4182868
 *
 * @param <T> the type of the input elements
 * @param <K> the type of keys stored in the map
 * @param <V> the type of values stored in the map
 * @param <R> the output type of the collector
 * @author Gili Tzabari
 *         <p>
 *         Created on 11/28/2016.
 */
public final class MultimapCollector<T, K, V, R extends Multimap<K, V>> implements Collector<T, R, R> {
    private final Supplier<R> mapSupplier;
    private final Function<? super T, ? extends K> keyMapper;
    private final Function<? super T, ? extends V> valueMapper;

    /**
     * Creates a new MultimapCollector.
     * <p>
     *
     * @param mapSupplier a function which returns a new, empty {@code Multimap} into which intermediate results will be
     *                    inserted
     * @param keyMapper   a function that transforms the map keys
     * @param valueMapper a function that transforms the map values
     * @throws NullPointerException if any of the arguments are null
     */
    public MultimapCollector(Supplier<R> mapSupplier,
                             Function<? super T, ? extends K> keyMapper,
                             Function<? super T, ? extends V> valueMapper) {
        this.mapSupplier = mapSupplier;
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
    }

    @Override
    public Supplier<R> supplier() {
        return mapSupplier;
    }

    @Override
    public BiConsumer<R, T> accumulator() {
        return (map, entry) ->
        {
            K key = keyMapper.apply(entry);
            if (key == null) {
                throw new IllegalArgumentException("keyMapper(" + entry + ") returned null");
            }
            V value = valueMapper.apply(entry);
            if (value == null) {
                throw new IllegalArgumentException("keyMapper(" + entry + ") returned null");
            }
            map.put(key, value);
        };
    }

    @Override
    public BinaryOperator<R> combiner() {
        return (left, right) ->
        {
            left.putAll(right);
            return left;
        };
    }

    @Override
    public Function<R, R> finisher() {
        return Function.identity();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.noneOf(Characteristics.class);
    }
}

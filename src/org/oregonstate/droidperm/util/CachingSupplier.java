package org.oregonstate.droidperm.util;

import java.util.function.Supplier;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 4/6/2016.
 */
public class CachingSupplier<T> implements Supplier<T> {

    private T data;
    private Supplier<T> supplier;

    public CachingSupplier(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (data == null) {
            data = supplier.get();
        }
        return data;
    }
}

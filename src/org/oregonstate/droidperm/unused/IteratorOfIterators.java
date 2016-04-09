package org.oregonstate.droidperm.unused;

import java.util.Iterator;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 4/7/2016.
 */
public abstract class IteratorOfIterators<T, U> implements Iterator<U> {

    private Iterator<T> outerIterator;
    private Iterator<U> innerIterator;

    public IteratorOfIterators(Iterator<T> outerIterator) {
        this.outerIterator = outerIterator;
    }

    @Override
    public boolean hasNext() {
        updateInner();
        return innerIterator != null && innerIterator.hasNext();
    }

    @Override
    public U next() {
        updateInner();
        return innerIterator.next();
    }

    private void updateInner() {
        while ((innerIterator == null || !innerIterator.hasNext()) && outerIterator.hasNext()) {
            innerIterator = getIteratorFor(outerIterator.next());
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public abstract Iterator<U> getIteratorFor(T outer);
}

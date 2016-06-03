package org.oregonstate.droidperm.consumer.method;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/5/2016.
 * <p>
 * Source: http://stackoverflow.com/a/18586688/4182868
 */

import soot.Context;
import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Objects;

/**
 * Container to ease passing around a tuple of two objects. This object provides a sensible implementation of equals(),
 * returning true if equals() is true on each of the contained objects.
 */
public class MethodInContext {
    public final MethodOrMethodContext method;
    public final Edge edge;

    /**
     * Constructor for a MethodInContext.
     *
     * @param method the method in the MethodInContext
     */
    public MethodInContext(MethodOrMethodContext method) {
        this.method = method;
        this.edge = null;
    }

    public MethodInContext(Edge edge) {
        this.method = edge.getTgt();
        this.edge = edge;
    }

    public Context getContext() {
        return edge != null ? edge.srcStmt() : null;
    }

    /**
     * Checks the two objects for equality by delegating to their respective {@link Object#equals(Object)} methods.
     *
     * @param o the {@link MethodInContext} to which this one is to be checked for equality
     * @return true if the underlying objects of the MethodInContext are both considered equal
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodInContext)) {
            return false;
        }
        MethodInContext param = (MethodInContext) o;
        return Objects.equals(param.method, method) && Objects.equals(param.edge, edge);
    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     *
     * @return a hashcode of the MethodInContext
     */
    @Override
    public int hashCode() {
        return (method == null ? 0 : method.hashCode()) ^ (edge == null ? 0 : edge.hashCode());
    }

    @Override
    public String toString() {
        return method + " called from " + getContext();
    }
}

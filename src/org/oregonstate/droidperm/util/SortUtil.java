package org.oregonstate.droidperm.util;

import org.oregonstate.droidperm.perm.miner.jaxb_out.PermissionDef;
import soot.MethodOrMethodContext;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Comparator;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 4/23/2016.
 */
public class SortUtil {

    public static final Comparator<SootClass> classComparator = Comparator.comparing(SootClass::getName);

    public static final Comparator<SootMethod> methodComparator =
            Comparator.comparing(SootMethod::getDeclaringClass, classComparator)
                    .thenComparingInt(SootMethod::getJavaSourceStartLineNumber);

    public static final Comparator<MethodOrMethodContext> methodOrMCComparator =
            Comparator.comparing(MethodOrMethodContext::method, methodComparator);

    public static final Comparator<Edge> edgeComparator = Comparator.comparing(Edge::getTgt, methodOrMCComparator)
            .thenComparing(Edge::getSrc, methodOrMCComparator);

    public static final Comparator<PermissionDef> permissionDefComparator =
            Comparator.comparing(PermissionDef::getClassName)
                    .thenComparing(PermissionDef::getTarget, Comparator.nullsFirst(Comparator.naturalOrder()));
}

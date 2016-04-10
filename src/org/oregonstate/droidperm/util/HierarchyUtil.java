package org.oregonstate.droidperm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.util.ArraySet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 3/24/2016.
 */
public class HierarchyUtil {
    private static final Logger logger = LoggerFactory.getLogger(HierarchyUtil.class);

    /**
     * If true, allow points-to of type AnySubType. If false, throw exception for AnySubType.
     */
    private static final boolean ALLOW_ANY_SUBTYPE = false;

    public static List<SootMethod> resolveAbstractDispatches(Collection<SootMethodAndClass> methodDefs) {
        return methodDefs.stream().map(HierarchyUtil::resolveAbstractDispatch)
                .flatMap(Collection::stream).collect(Collectors.toList());
    }

    private static List<SootMethod> resolveAbstractDispatch(SootMethodAndClass methodDef) {
        Scene scene = Scene.v();
        SootClass clazz = scene.getSootClassUnsafe(methodDef.getClassName());
        if (clazz == null) {
            return Collections.emptyList();
        } else {
            SootMethod method = scene.grabMethod(methodDef.getSignature());
            if (method == null) {
                logger.warn("Nonexistent producer/consumer method: " + methodDef);
                return Collections.emptyList();
            }

            return scene.getActiveHierarchy().resolveAbstractDispatch(clazz, method);
        }
    }

    /**
     * For concrete types use concrete dispatch, for types of class AnySubType use abstract dispatch.
     * <p>
     * todo check if abstract dispatch is really needed
     */
    public static List<SootMethod> resolveHybridDispatch(SootMethod staticTargetMethod,
                                                         Set<Type> targetPossibleTypes) {
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
        Set<SootMethod> set = new ArraySet<>();
        for (Type cls : new ArrayList<>(targetPossibleTypes)) {
            if (cls instanceof RefType)
                set.add(hierarchy.resolveConcreteDispatch(((RefType) cls).getSootClass(), staticTargetMethod));
            else if (cls instanceof ArrayType) {
                set.add(hierarchy
                        .resolveConcreteDispatch((RefType.v("java.lang.Object")).getSootClass(), staticTargetMethod));
            } else if (cls instanceof AnySubType && ALLOW_ANY_SUBTYPE) {
                set.addAll(hierarchy
                        .resolveAbstractDispatch(((AnySubType) cls).getBase().getSootClass(), staticTargetMethod));
            } else throw new RuntimeException("Unable to resolve concrete dispatch of type " + cls);
        }

        return Collections.unmodifiableList(new ArrayList<>(set));
    }
}

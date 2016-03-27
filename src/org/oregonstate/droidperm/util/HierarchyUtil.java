package org.oregonstate.droidperm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu>
 *         Created on 3/24/2016.
 */
public class HierarchyUtil {
    private static final Logger logger = LoggerFactory.getLogger(HierarchyUtil.class);

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
}

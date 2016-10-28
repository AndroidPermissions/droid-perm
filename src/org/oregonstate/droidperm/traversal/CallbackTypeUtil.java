package org.oregonstate.droidperm.traversal;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/13/2016.
 */
public class CallbackTypeUtil {

    //Double-brace initialziation: http://stackoverflow.com/a/6802512/4182868
    private static Map<String, List<String>> componentDefs = new HashMap<String, List<String>>() {{
        put("Activity", Collections.singletonList("android.app.Activity"));
        put("Fragment", Arrays.asList("android.app.Fragment", "android.support.v4.app.Fragment"));
        put("Service", Collections.singletonList("android.app.Service"));
    }};

    private static Map<String, List<SootClass>> componentTypes = componentDefs.keySet().stream().collect(
            Collectors.toMap(
                    comp -> comp,
                    comp -> componentDefs.get(comp).stream().filter(Scene.v()::containsClass)
                            .map(Scene.v()::getSootClass).collect(Collectors.toList())
            ));

    public static String getCallbackType(MethodOrMethodContext callback) {
        SootClass cls = callback.method().getDeclaringClass();
        List<SootClass> superclasses = Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(cls);
        while (cls != null) {
            for (String compType : componentTypes.keySet()) {
                if (!Collections.disjoint(componentTypes.get(compType), superclasses)) {
                    return compType;
                }
            }
            if (cls.isInnerClass()) {
                cls = cls.getOuterClass();
                superclasses = Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(cls);
            } else {
                cls = null;
            }
        }
        return "Unknown";
    }

    public static List<String> getCallbackTypes(Collection<MethodOrMethodContext> callbacks) {
        return callbacks.stream().map(CallbackTypeUtil::getCallbackType).distinct().sorted()
                .collect(Collectors.toList());
    }
}

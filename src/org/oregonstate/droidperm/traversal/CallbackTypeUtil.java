package org.oregonstate.droidperm.traversal;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.oregonstate.droidperm.util.MyCollectors;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 8/13/2016.
 */
public class CallbackTypeUtil {

    //Double-brace initialziation: http://stackoverflow.com/a/6802512/4182868
    private static Multimap<String, String> componentDefs = ImmutableMultimap.<String, String>builder()
            .putAll("Activity", Collections.singletonList("android.app.Activity"))
            .putAll("Fragment", Arrays.asList("android.app.Fragment", "android.support.v4.app.Fragment"))
            .putAll("Service", Collections.singletonList("android.app.Service"))
            .build();

    private static Multimap<String, SootClass> componentTypes = componentDefs.keySet().stream().collect(
            MyCollectors.toMultimap(
                    comp -> comp,
                    comp -> componentDefs.get(comp).stream().filter(Scene.v()::containsClass)
                            .map(Scene.v()::getSootClass)
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

package org.oregonstate.droidperm.util;

import soot.Scene;
import soot.SootMethod;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class SceneUtil {

    public static List<SootMethod> grabMethods(List<String> signatures) {
        Predicate<String> methodSigTester = Pattern.compile("^\\s*<(.*?):\\s*(.*?)>\\s*$").asPredicate();

        return signatures.stream().map(sig -> {
            if (!methodSigTester.test(sig)) {
                throw new RuntimeException(("Invalid method signature: " + sig));
            }
            return Scene.v().grabMethod(sig.trim());
        }).filter(method -> method != null).collect(Collectors.toList());
    }
}

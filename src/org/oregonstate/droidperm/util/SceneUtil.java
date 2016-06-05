package org.oregonstate.droidperm.util;

import soot.Scene;
import soot.SootMethod;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class SceneUtil {

    public static final String EXTENDS_PREFIX = "? extends ";

    /**
     * For entries starting with "? extends ", all implementing methods will be grabbed.
     */
    public static Set<SootMethod> grabMethods(List<String> signatures) {
        Predicate<String> methodSigTester = Pattern.compile("^\\s*<(.*?):\\s*(.*?)>\\s*$").asPredicate();

        return signatures.stream().map(sig -> resolveSigQuery(methodSigTester, sig))
                .collect(MyCollectors.toFlatSet());
    }

    private static List<SootMethod> resolveSigQuery(Predicate<String> methodSigTester, String sigQuery) {
        String sig;
        boolean ignoreInHierarchy;
        if (sigQuery.startsWith(EXTENDS_PREFIX)) {
            sig = sigQuery.substring(EXTENDS_PREFIX.length());
            ignoreInHierarchy = true;
        } else {
            sig = sigQuery;
            ignoreInHierarchy = false;
        }

        if (!methodSigTester.test(sig)) {
            throw new RuntimeException(("Invalid method signature: " + sig));
        }

        SootMethod method = Scene.v().grabMethod(sig.trim());
        if (method == null) {
            return Collections.emptyList();
        }
        if (!ignoreInHierarchy) {
            return Collections.singletonList(method);
        } else {
            return Scene.v().getActiveHierarchy().resolveAbstractDispatch(method.getDeclaringClass(), method);
        }
    }
}

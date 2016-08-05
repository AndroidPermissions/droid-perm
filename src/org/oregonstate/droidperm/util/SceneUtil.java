package org.oregonstate.droidperm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.toolkits.scalar.Pair;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class SceneUtil {

    private static final Logger logger = LoggerFactory.getLogger(SceneUtil.class);
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

    /**
     * @return A map from sensitives to actual Statements in context possibly invoking that sensitive.
     */
    @SuppressWarnings("Convert2streamapi")
    public static MultiMap<SootMethod, Pair<Stmt, SootMethod>> resolveMethodUsages(Set<SootMethod> sensitives) {
        Hierarchy hier = Scene.v().getActiveHierarchy();

        //for performance optimization
        Set<String> sensSubsignatures =
                sensitives.stream().map(SootMethod::getSubSignature).collect(Collectors.toSet());

        MultiMap<SootMethod, Pair<Stmt, SootMethod>> result = new HashMultiMap<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.isConcrete()) {
                for (SootMethod contextMeth : sc.getMethods()) {
                    if (!contextMeth.isConcrete()) {
                        continue;
                    }

                    Body body;
                    try {
                        body = contextMeth.retrieveActiveBody();
                    } catch (Exception e) {
                        logger.warn(e.getMessage());
                        continue;
                    }

                    for (Unit u : body.getUnits()) {
                        Stmt stmt = (Stmt) u;
                        if (stmt.containsInvokeExpr()) {
                            InvokeExpr invoke = stmt.getInvokeExpr();
                            SootMethod invokeMethod;
                            try {
                                invokeMethod = invoke.getMethod();
                            } catch (Exception e) {
                                continue;
                            }

                            if (sensSubsignatures.contains(invokeMethod.getSubSignature())) {
                                List<SootMethod> resolvedMethods = hier.resolveAbstractDispatch(
                                        invokeMethod.getDeclaringClass(), invoke.getMethod());
                                if (!Collections.disjoint(resolvedMethods, sensitives)) {
                                    //this unit calls a sensitive.
                                    SootMethod sens = resolvedMethods.stream().filter(sensitives::contains).findAny()
                                            .orElse(null);
                                    result.put(sens, new Pair<>(stmt, contextMeth));
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static MultiMap<SootMethod, Pair<Stmt, SootMethod>> resolveMethodUsages(
            Collection<? extends SootMethodAndClass> methodDefs) {
        return resolveMethodUsages(new HashSet<SootMethod>(HierarchyUtil.resolveAbstractDispatches(methodDefs)));
    }
}

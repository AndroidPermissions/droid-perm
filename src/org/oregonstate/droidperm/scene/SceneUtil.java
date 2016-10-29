package org.oregonstate.droidperm.scene;

import org.oregonstate.droidperm.util.MyCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                    } catch (NullPointerException e) {
                        //Redundant.
                        //This one is thrown after other types of exceptions were thrown previously for same method.
                        continue;
                    } catch (Exception e) {
                        logger.warn("Exception in retrieveActiveBody() for " + contextMeth + " : " + e.toString());
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
                                logger.debug("Exception in getMethod() for " + invoke + " : " + e.toString());
                                continue;
                            }

                            if (sensSubsignatures.contains(invokeMethod.getSubSignature())) {
                                List<SootMethod> resolvedMethods = hier.resolveAbstractDispatch(
                                        invokeMethod.getDeclaringClass(), invokeMethod);
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

    public static MultiMap<SootMethod, Pair<Stmt, SootMethod>> resolveMethodUsages(Collection<SootMethod> sootMethods) {
        return resolveMethodUsages(new HashSet<>(sootMethods));
    }

    /**
     * @return A map from fields to actual statements in context possibly referring that fields.
     */
    public static MultiMap<SootField, Pair<Stmt, SootMethod>> resolveFieldUsages(Set<SootField> sensFields) {
        MultiMap<SootField, Pair<Stmt, SootMethod>> result = new HashMultiMap<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.isConcrete()) {
                for (SootMethod contextMeth : sc.getMethods()) {
                    if (!contextMeth.isConcrete()) {
                        continue;
                    }

                    Body body;
                    try {
                        body = contextMeth.retrieveActiveBody();
                    } catch (NullPointerException e) {
                        //Redundant.
                        //This one is thrown after other types of exceptions were thrown previously for same method.
                        continue;
                    } catch (Exception e) {
                        logger.warn("Exception in retrieveActiveBody() for " + contextMeth + " : " + e.toString());
                        continue;
                    }

                    for (Unit u : body.getUnits()) {
                        Stmt stmt = (Stmt) u;
                        List<SootField> fields = getReferredFields(stmt);
                        //noinspection Convert2streamapi
                        for (SootField field : fields) {
                            if (sensFields.contains(field)) {
                                result.put(field, new Pair<>(stmt, contextMeth));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static MultiMap<SootField, Pair<Stmt, SootMethod>> resolveFieldUsages(Collection<SootField> sensFields) {
        return resolveFieldUsages(new HashSet<>(sensFields));
    }

    private static List<SootField> getReferredFields(Stmt stmt) {
        if (stmt instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt) stmt;
            return Stream.of(assign.getLeftOp(), assign.getRightOp())
                    .filter(val -> val instanceof FieldRef)
                    .map(fieldRef -> ((FieldRef) fieldRef).getField())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

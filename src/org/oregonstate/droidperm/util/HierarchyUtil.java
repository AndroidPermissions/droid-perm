package org.oregonstate.droidperm.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.util.ArraySet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 3/24/2016.
 */
public class HierarchyUtil {
    private static final Logger logger = LoggerFactory.getLogger(HierarchyUtil.class);

    private enum AnySubTypeTreatment {
        /**
         * When AnySubType is encountered in PointsTo data, method call is resolved through abstract dispatch on its
         * base type. This is the most permissive treatment.
         * <p>
         * Spark/Geom generates ANY_SUBTYPE for the result of some native method calls, like Thread.currentThread().
         * This behavior is Hardcoded in Soot.
         * <p>
         * Works for: lib_callback_ex + util_io
         * <p>
         * Crashes for: lib_callback_ex + JDK.
         * <p>
         * Context: Attempt to resolve the method getChars() in the context of AbstractStringBuffer, where PointsTo =
         * {String, * extends Object, * extends 3 other types}. Only String is a valid target for this method call, and
         * resolving it is not relevant for the purpose of reaching sensitive methods.
         */
        ALLOW_ALL,

        /**
         * Resolve AnySubType through abstract dispatch on base class, like for ALLOW_ANY. If an exception is thrown due
         * to the type being incompatible with the called method (likely due to a Spark bug), ignore that target type.
         * This solution is more permissive than ALLOW_ALL.
         * <p>
         * Works for/Problems for: same as for treatment IGNORE.
         */
        ALLOW_VALID,

        /**
         * PointsTo targets of type AnySubType are ignored.
         * <p>
         * Required for: misc_tests + JDK
         * <p>
         * Problems for: lib_callback_executor+JDK, returns an infeasible path, which doesn't use executors.
         * <p>
         * Works for: Executors, lib_callback_ex + util_io, misc_tests + util_io
         * <p>
         * Problems for: lib_callback_ex + JDK, misc_tests + JDK. Same problem.
         * <p>
         * Problem description: Paths are found, but are unfeasible and don't pass through ExecutorService. Technically
         * they are feasible even for k-CFA CG, it would require symbolic execution or other customizations to reject
         * this path.
         * <p>
         * Explanation: Case util+io discovers only the correct path and reports it, on all treatments. Case JDK
         * discovers both the correct (length 16) and the wrong path (length 15) The wrong path is discovered first,
         * because we use BFS and the wrong one is shorter. Thus the wrong path is the preferred one.
         */
        IGNORE,

        /**
         * When AnySubType is encountered in PointsTo data, an exception is thrown.
         * <p>
         * Works for: up to Executors.
         * <p>
         * Crashes for: lib_callback_ex + (JDK / util_io), on Thread.currentThread().interrupt(), called from
         * AsyncHttpClient library.
         */
        FORBID
    }

    private static final AnySubTypeTreatment ANY_SUBTYPE_TREATMENT = AnySubTypeTreatment.ALLOW_VALID;

    private static boolean dispatchExceptionLogged = false;

    public static List<SootMethod> resolveAbstractDispatches(Collection<? extends SootMethodAndClass> methodDefs,
                                                             boolean ignoreUnresolved) {
        return methodDefs.stream().map(methodDef -> resolveAbstractDispatch(methodDef, ignoreUnresolved))
                .flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * Includes all initial defs as keys, including those with 0 results.
     */
    public static <T extends SootMethodAndClass> ListMultimap<T, SootMethod> resolveAbstractDispatchesToMap(
            Collection<T> methodDefs) {
        return methodDefs.stream().collect(MyCollectors.toMultimapForCollection(
                ArrayListMultimap::create,
                methodDef -> methodDef,
                methodDef -> resolveAbstractDispatch(methodDef, false)
        ));
    }

    public static List<SootMethod> resolveAbstractDispatch(SootMethodAndClass methodDef, boolean ignoreUnresolved) {
        Scene scene = Scene.v();
        SootClass clazz = scene.getSootClassUnsafe(methodDef.getClassName());
        if (clazz == null) {
            return Collections.emptyList();
        } else if (clazz.isPhantom() && !ignoreUnresolved) {
            throw new RuntimeException("Checker/sensitive declaring class is phantom: " + clazz);
        } else {
            SootMethod method = scene.grabMethod(methodDef.getSignature());

            //Workaround for soot bug: sometimes scene contains methods with name wrapped into ''.
            //Example: SubscriptionManager.from()
            if (method == null) {
                method = scene.grabMethod(getSignatureWithQuotes(methodDef));
            }

            if (method != null) {
                return scene.getActiveHierarchy().resolveAbstractDispatch(clazz, method);
            } else {
                logger.warn("Class " + clazz + " is in Scene but method " + methodDef.getSignature() + " is not.");
                System.err.println("Existing methods in " + clazz + " :");
                clazz.getMethods().forEach(System.err::println);
                return Collections.emptyList();
            }
        }
    }

    /**
     * Same as SootMethodAndClass.getSignature(), but method name is wrapped into ''
     */
    private static String getSignatureWithQuotes(SootMethodAndClass methodDef) {
        String s = "<" + methodDef.getClassName() + ": " + (methodDef.getReturnType().length() == 0 ? "" :
                                                            methodDef.getReturnType() + " ")
                + "'" + methodDef.getMethodName() + "'" + "(";
        for (int i = 0; i < methodDef.getParameters().size(); i++) {
            if (i > 0) {
                s += ",";
            }
            s += methodDef.getParameters().get(i).trim();
        }
        s += ")>";
        return s;
    }

    public static List<SootMethod> resolveHybridDispatch(SootMethod staticTargetMethod,
                                                         Set<Type> targetPossibleTypes) {
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
        Set<SootMethod> set = new ArraySet<>();
        for (Type cls : new ArrayList<>(targetPossibleTypes)) {
            if (cls instanceof RefType) {
                try {
                    set.add(hierarchy.resolveConcreteDispatch(((RefType) cls).getSootClass(), staticTargetMethod));
                } catch (Exception e) {
                    //Exceptions here might happen for Object.equals(), Object.hashCode() and other classes
                    // probably when they are phantom.
                    if (!dispatchExceptionLogged) {
                        logger.error("Exception in resolveConcreteDispatch(). "
                                + "Other exceptions of this type won't be logged.", e);
                        dispatchExceptionLogged = true;
                    }
                }
            } else if (cls instanceof ArrayType) {
                set.add(hierarchy
                        .resolveConcreteDispatch((RefType.v("java.lang.Object")).getSootClass(), staticTargetMethod));
            } else if (cls instanceof AnySubType) {
                switch (ANY_SUBTYPE_TREATMENT) {
                    case ALLOW_ALL:
                        set.addAll(hierarchy.resolveAbstractDispatch(((AnySubType) cls).getBase().getSootClass(),
                                staticTargetMethod));
                        break;
                    case ALLOW_VALID:
                        try {
                            set.addAll(hierarchy.resolveAbstractDispatch(((AnySubType) cls).getBase().getSootClass(),
                                    staticTargetMethod));
                        } catch (RuntimeException e) {
                            //Happens too many times.
                            // 2200 sequences of the same 4 classes, likely all from AbstractStringBuffer.
                            logger.debug("Hybrid dispatch ignored for: " + cls + ". Details: " + e.getMessage());
                        }
                        break;
                    case IGNORE:
                        break; //ignore
                    case FORBID:
                    default:
                        throw new RuntimeException("Unable to resolve hybrid dispatch, AnySubType not allowed: " + cls);
                }
            } else {
                throw new RuntimeException("Unable to resolve hybrid dispatch, unknown type: " + cls);
            }
        }

        return Collections.unmodifiableList(new ArrayList<>(set));
    }
}

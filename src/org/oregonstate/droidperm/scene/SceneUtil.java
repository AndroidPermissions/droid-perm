package org.oregonstate.droidperm.scene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.tagkit.IntegerConstantValueTag;
import soot.tagkit.StringConstantValueTag;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 5/30/2016.
 */
public class SceneUtil {

    private static final Logger logger = LoggerFactory.getLogger(SceneUtil.class);
    public static final String EXTENDS_PREFIX = "? extends ";
    private static Map<Unit, SootMethod> stmtToMethodMap;

    /**
     * For entries starting with "? extends ", all implementing methods will be grabbed.
     */
    public static Set<SootMethod> grabMethods(List<String> signatures) {
        Predicate<String> methodSigTester = Pattern.compile("^\\s*<(.*?):\\s*(.*?)>\\s*$").asPredicate();

        return signatures.stream().flatMap(sig -> resolveSigQuery(methodSigTester, sig).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
     * Traverse all statements in the given classes and apply stmtConsumer to each of them.
     */
    @SafeVarargs
    public static void traverseClasses(Collection<SootClass> classes, Predicate<SootMethod> classpathFilter,
                                       BiConsumer<Stmt, SootMethod>... stmtConsumers) {
        Collection<BiConsumer<Stmt, SootMethod>> consumers = ImmutableList.copyOf(stmtConsumers);
        if (classpathFilter == null) {
            classpathFilter = meth -> true;
        }
        classes.stream()
                .flatMap(sc -> sc.getMethods().stream()).filter(SootMethod::isConcrete).filter(classpathFilter)
                .map(SceneUtil::retrieveBody).filter(Objects::nonNull)
                .forEach(body -> body.getUnits().forEach(unit -> {
                    Stmt stmt = (Stmt) unit;
                    consumers.forEach(consumer -> consumer.accept(stmt, body.getMethod()));
                }));
    }

    /**
     * Traverse the CHA call graph starting from dummyMain and apply stmtConsumer to each of them.
     */
    @SafeVarargs
    public static void traverseCHACallGraph(SootMethod dummyMain, Predicate<SootMethod> classpathFilter,
                                            BiConsumer<Stmt, SootMethod>... stmtConsumers) {
        Collection<BiConsumer<Stmt, SootMethod>> consumers = ImmutableList.copyOf(stmtConsumers);
        if (classpathFilter == null) {
            classpathFilter = meth -> true;
        }
        Set<SootMethod> reached = new HashSet<>();
        Queue<SootMethod> queue = new ArrayDeque<>();
        reached.add(dummyMain);
        queue.add(dummyMain);

        //Preferred over ListMultimap for performance reasons.
        Map<SootMethod, List<SootMethod>> invokeDispatchesCache = new HashMap<>();

        for (SootMethod crntMeth = queue.poll(); crntMeth != null; crntMeth = queue.poll()) {
            if (crntMeth.isConcrete() && crntMeth.hasActiveBody() &&
                    //only analyze the body of methods accepted by classpathFilter
                    classpathFilter.test(crntMeth)) {
                Body body = retrieveBody(crntMeth);
                if (body == null) {
                    continue;
                }
                body.getUnits().forEach(
                        (Unit unit) -> {
                            Stmt stmt = (Stmt) unit;
                            consumers.forEach(consumer -> consumer.accept(stmt, body.getMethod()));

                            if (stmt.containsInvokeExpr()) {
                                InvokeExpr invoke = stmt.getInvokeExpr();
                                SootMethod invokeMethod;
                                try {
                                    invokeMethod = invoke.getMethod();
                                } catch (Exception e) {
                                    logger.debug("Exception in getMethod() for " + invoke + " : " + e.toString());
                                    return;
                                }

                                if (!invokeDispatchesCache.containsKey(invokeMethod)) {
                                    try {
                                        invokeDispatchesCache.put(invokeMethod, Scene.v().getActiveHierarchy()
                                                .resolveAbstractDispatch(invokeMethod.getDeclaringClass(),
                                                        invokeMethod));
                                    } catch (Exception e) {
                                        //Happens if a concrete class doesn't implement a method from an implemented
                                        //interface. Which in turn happens when some linked jar versions are mismatched.
                                        //Another possibility is a class hierarchy containing a phantom class.
                                        logger.error(e.getMessage(), e);
                                    }
                                }
                                if (invokeDispatchesCache.containsKey(invokeMethod)) {
                                    for (SootMethod resolvedMeth : invokeDispatchesCache.get(invokeMethod)) {
                                        if (!reached.contains(resolvedMeth)) {
                                            reached.add(resolvedMeth);
                                            queue.add(resolvedMeth);
                                        }
                                    }
                                }
                            }
                        });
            }
        }
    }

    public static Body retrieveBody(SootMethod meth) {
        try {
            return meth.retrieveActiveBody();
        } catch (NullPointerException e) {
            //Redundant.
            //This one is thrown after other types of exceptions were thrown previously for same method.
        } catch (Exception e) {
            logger.warn("Exception in retrieveActiveBody() for " + meth + " : " + e.toString());
        }
        return null;
    }

    /**
     * @param result Multimap where result will be collected. A multimap from methods to statements possibly invoking
     *               that methods.
     * @return a Pair consisting of the BiConsumer that has to be passed to traverseClasses to collect all method
     * usages, and the result where these usages will be collected.
     */
    public static BiConsumer<Stmt, SootMethod> createMethodUsagesCollector(
            Collection<SootMethod> sootMethods, Multimap<SootMethod, Stmt> result) {
        Set<SootMethod> methodSet = sootMethods instanceof Set ? (Set) sootMethods : new HashSet<>(sootMethods);

        //for performance optimization
        Set<String> sensSubsignatures =
                methodSet.stream().map(SootMethod::getSubSignature).collect(Collectors.toSet());

        return (stmt, method) -> collectResolvedMethods(methodSet, sensSubsignatures, result, stmt);
    }

    private static void collectResolvedMethods(Set<SootMethod> sensitives, Set<String> sensSubsignatures,
                                               Multimap<SootMethod, Stmt> result, Stmt stmt) {
        if (stmt.containsInvokeExpr()) {
            InvokeExpr invoke = stmt.getInvokeExpr();
            SootMethod invokeMethod;
            try {
                invokeMethod = invoke.getMethod();
            } catch (Exception e) {
                logger.debug("Exception in getMethod() for " + invoke + " : " + e.toString());
                return;
            }

            if (sensSubsignatures.contains(invokeMethod.getSubSignature())) {
                List<SootMethod> resolvedMethods = Scene.v().getActiveHierarchy().resolveAbstractDispatch(
                        invokeMethod.getDeclaringClass(), invokeMethod);
                if (!Collections.disjoint(resolvedMethods, sensitives)) {
                    //this unit calls a sensitive.
                    SootMethod sens = resolvedMethods.stream().filter(sensitives::contains).findAny().orElse(null);
                    result.put(sens, stmt);
                }
            }
        }
    }

    /**
     * @param result Multimap where result will be collected. A multimap from fields to statements possibly referring
     *               that fields.
     * @return a Pair consisting of the BiConsumer that has to be passed to traverseClasses to collect all field usages,
     * and the result where these usages will be collected.
     */
    public static BiConsumer<Stmt, SootMethod> createFieldUsagesCollector(
            Collection<SootField> sensFields, Multimap<SootField, Stmt> result) {
        Set<SootField> sensFieldsSet = sensFields instanceof Set ? (Set) sensFields : new HashSet<>(sensFields);
        Map<String, SootField> stringConstantFieldsMap = buildStringConstantFieldsMap(sensFieldsSet);
        Map<Integer, SootField> intConstantFieldsMap = buildIntConstantFieldsMap(sensFieldsSet);
        return (stmt, method)
                -> collectResolvedFields(sensFieldsSet, stringConstantFieldsMap, intConstantFieldsMap, result, stmt);
    }

    private static void collectResolvedFields(Set<SootField> sensFields, Map<String, SootField> stringConstantFieldsMap,
                                              Map<Integer, SootField> intConstantFieldsMap,
                                              Multimap<SootField, Stmt> result, Stmt stmt) {
        List<SootField> fields = getReferredFields(stmt, stringConstantFieldsMap, intConstantFieldsMap);
        fields.stream().filter(sensFields::contains).forEach(field ->
                result.put(field, stmt)
        );
    }

    public static Map<String, SootField> buildStringConstantFieldsMap(Set<SootField> sensFields) {
        //If there are multiple sensitive fields initialized with same constant, exception will be thrown.
        return sensFields.stream().filter(field -> getStringConstantValue(field) != null).collect(Collectors.toMap(
                SceneUtil::getStringConstantValue,
                field -> field
        ));
    }

    public static String getStringConstantValue(SootField field) {
        return field.getTags().stream().filter(tag -> tag instanceof StringConstantValueTag)
                .map(constTag -> ((StringConstantValueTag) constTag).getStringValue())
                .findAny().orElse(null);
    }

    /**
     * Includes in the output only te fields that may be arguments to TelephonyManager.listen()
     */
    public static Map<Integer, SootField> buildIntConstantFieldsMap(Set<SootField> sensFields) {
        //If there are multiple sensitive fields initialized with same constant, exception will be thrown.
        return sensFields.stream()
                .filter(field -> getIntConstantValue(field) != null
                        && field.getDeclaringClass().getName().equals("android.telephony.PhoneStateListener"))
                .collect(Collectors.toMap(
                        SceneUtil::getIntConstantValue,
                        field -> field
                ));
    }

    public static Integer getIntConstantValue(SootField field) {
        return field.getTags().stream().filter(tag -> tag instanceof IntegerConstantValueTag)
                .map(constTag -> ((IntegerConstantValueTag) constTag).getIntValue())
                .findAny().orElse(null);
    }

    private static List<SootField> getReferredFields(Stmt stmt, Map<String, SootField> stringConstantFieldsMap,
                                                     Map<Integer, SootField> intConstantFieldsMap) {
        List<SootField> result = new ArrayList<>();

        //On incomplete code we might get field references on phantom classes. They should be skipped.
        if (stmt.containsFieldRef() && !stmt.getFieldRef().getFieldRef().declaringClass().isPhantom()) {
            SootField field = resolve(stmt.getFieldRef());
            if (field != null) {
                result.add(field);
            }
        }
        //If the referred field is a String constant, it will be inlined into the statement using it.
        //Thus we have to serch it among the constants.
        getStringConstantsIfAny(stmt).stream().filter(stringConstantFieldsMap::containsKey)
                .forEach(stringConst -> result.add(stringConstantFieldsMap.get(stringConst)));

        //If the referred field is an int constant, it will be inlined into the statement using it.
        //Thus we have to serch it among the constants.
        //Only including int constants that are arguments to TelephonyManager.listen()
        String telephonyManagerListen =
                "<android.telephony.TelephonyManager: void listen(android.telephony.PhoneStateListener,int)>";
        if (stmt.containsInvokeExpr() && stmt.getInvokeExpr().getMethodRef().getSignature()
                .equals(telephonyManagerListen)) {
            getIntConstantsIfAny(stmt).stream()
                    .flatMap(argInt -> intConstantFieldsMap.keySet().stream()
                            .filter(fieldInt -> (fieldInt & argInt) != 0))
                    .forEach(intConst -> result.add(intConstantFieldsMap.get(intConst)));
        }

        return result;
    }

    private static SootField resolve(FieldRef fieldRef) {
        try {
            return fieldRef.getField();
        } catch (NullPointerException e) {
            //Redundant.
            //This one is thrown after other types of exceptions were thrown previously for same method.
        } catch (Exception e) {
            logger.warn("Exception in FieldRef.getField() for " + fieldRef + " : " + e.toString());
        }
        return null;
    }

    public static Multimap<String, Stmt> resolveConstantUsages(Collection<String> constants,
                                                               Predicate<SootMethod> classpathFilter) {
        Set<String> constantsSet = constants instanceof Set ? (Set) constants : new HashSet<>(constants);
        Multimap<String, Stmt> result = HashMultimap.create();
        traverseClasses(Scene.v().getApplicationClasses(), classpathFilter,
                (stmt, method) -> collectResolvedConstants(constantsSet, result, stmt));
        return result;
    }

    private static void collectResolvedConstants(Set<String> constantsSet, Multimap<String, Stmt> result, Stmt stmt) {
        List<String> referredConst = getStringConstantsIfAny(stmt);
        referredConst.stream().filter(constantsSet::contains).forEach(constant ->
                result.put(constant, stmt)
        );
    }

    private static List<String> getStringConstantsIfAny(Stmt stmt) {
        return getConstantsIfAny(stmt, SceneUtil::getStringIfStringConstant);
    }

    private static List<Integer> getIntConstantsIfAny(Stmt stmt) {
        return getConstantsIfAny(stmt, SceneUtil::getIntIfIntConstant);
    }

    /**
     * Constants might be contained in either assignments on method calls (to my knowledge).
     *
     * @param valExtractor - function that takes a jimple Value and returns the constant of type ValT, if this Value
     *                     object represents such a constant, or null otherwise.
     */
    private static <ValT> List<ValT> getConstantsIfAny(Stmt stmt, Function<Value, ValT> valExtractor) {
        List<ValT> result = new ArrayList<>();
        if (stmt.containsInvokeExpr()) {
            List<Value> args = stmt.getInvokeExpr().getArgs();
            args.stream().map(valExtractor).filter(Objects::nonNull)
                    .forEach(result::add);
        }
        if (stmt instanceof AssignStmt) {
            ValT constValOrNull = valExtractor.apply(((AssignStmt) stmt).getRightOp());
            if (constValOrNull != null) {
                result.add(constValOrNull);
            }
        }
        return result;
    }

    private static Integer getIntIfIntConstant(Value value) {
        return value instanceof IntConstant ? ((IntConstant) value).value : null;
    }

    private static String getStringIfStringConstant(Value value) {
        return value instanceof StringConstant ? ((StringConstant) value).value : null;
    }

    /**
     * @return The container method of the given unit.
     */
    public static SootMethod getMethodOf(Unit unit) {
        if (stmtToMethodMap == null) {
            stmtToMethodMap = buildStmtToMethodMap();
        }
        return stmtToMethodMap.get(unit);
    }

    /**
     * Builds a map from all statements in the analysis classpath to their containing methods. Only methods reachable
     * through the call graph are included in this map.
     */
    private static Map<Unit, SootMethod> buildStmtToMethodMap() {
        Map<Unit, SootMethod> result = new HashMap<>();
        traverseClasses(Scene.v().getClasses(), null, result::put);
        return result;
    }
}

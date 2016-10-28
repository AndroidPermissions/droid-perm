package org.oregonstate.droidperm.perm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Denis Bogdanas <bogdanad@oregonstate.edu> Created on 10/14/2016.
 */
class AggregatePermDefProvider implements IPermissionDefProvider {

    private static final Logger logger = LoggerFactory.getLogger(AggregatePermDefProvider.class);

    private final Set<SootMethodAndClass> permCheckerDefs;
    private final Set<AndroidMethod> methodSensitiveDefs;
    private final Set<FieldSensitiveDef> fieldSensitiveDefs;

    public AggregatePermDefProvider(List<IPermissionDefProvider> sourceProviders) {
        this.permCheckerDefs = Collections.unmodifiableSet(computeAndVerifyUniqueSet(
                sourceProviders.stream().map(IPermissionDefProvider::getPermCheckerDefs),
                SootMethodAndClass::getSignature,
                "permission checker defs"));
        this.methodSensitiveDefs = Collections.unmodifiableSet(computeAndVerifyUniqueSet(
                sourceProviders.stream().map(IPermissionDefProvider::getMethodSensitiveDefs),
                SootMethodAndClass::getSignature,
                "method sensitive defs"));
        this.fieldSensitiveDefs = Collections.unmodifiableSet(computeAndVerifyUniqueSet(
                sourceProviders.stream().map(IPermissionDefProvider::getFieldSensitiveDefs),
                FieldSensitiveDef::getPseudoSignature,
                "field sensitive defs"));
    }

    private static <T> Set<T> computeAndVerifyUniqueSet(Stream<Set<T>> inputSetList,
                                                        Function<T, String> sigFunction,
                                                        final String itemsNameForLogging) {
        Set<T> result = new LinkedHashSet<>();
        Set<String> resultSigs = new HashSet<>();
        inputSetList.forEach(itemSetToAdd -> {
            Set<String> sigSetToAdd = itemSetToAdd.stream().map(sigFunction)
                    .collect(Collectors.toSet());
            if (Collections.disjoint(resultSigs, sigSetToAdd)) {
                result.addAll(itemSetToAdd);
                resultSigs.addAll(sigSetToAdd);
            } else {
                Set<String> intersection = new LinkedHashSet<>(resultSigs);
                intersection.retainAll(sigSetToAdd);
                logger.error("Common " + itemsNameForLogging + " found in 2 sources:");
                intersection.forEach(System.err::println);
                throw new RuntimeException("Common " + itemsNameForLogging + " found in 2 sources");
            }
        });
        return result;
    }

    @Override
    public Set<SootMethodAndClass> getPermCheckerDefs() {
        return permCheckerDefs;
    }

    @Override
    public Set<AndroidMethod> getMethodSensitiveDefs() {
        return methodSensitiveDefs;
    }

    @Override
    public Set<FieldSensitiveDef> getFieldSensitiveDefs() {
        return fieldSensitiveDefs;
    }
}
